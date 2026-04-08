package me.anno.support.jvm

import me.anno.support.jvm.JVMClassReader.Companion.API_LEVEL
import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.expression.SimpleCall
import me.anno.zauber.ast.simple.expression.SimpleGetField
import me.anno.zauber.ast.simple.expression.SimpleNumber
import me.anno.zauber.ast.simple.expression.SimpleSetField
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.specialization.Specialization.Companion.noSpecialization
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*

@Suppress("Since15")
class JVMMethodReader(val method: MethodLike, parameters: List<Parameter>) : MethodVisitor(API_LEVEL) {

    companion object {
        class ConstantsImpl {
            val scope = root
            val i0 = NumberExpression("0", scope, -1)
            val i1 = NumberExpression("1", scope, -1)
            val i2 = NumberExpression("2", scope, -1)
            val i3 = NumberExpression("3", scope, -1)
            val i4 = NumberExpression("4", scope, -1)
        }

        val Constants by threadLocal { ConstantsImpl() }
    }


    fun descToType(desc: String): Type {
        return SignatureReader(desc, methodScope).readType()
    }

    fun nameToType(desc: String): Type {
        // todo can be optimized
        return SignatureReader("L$desc;", methodScope).readType()
    }

    val methodScope get() = method.scope
    val classScope = methodScope.parent!!

    val graph = SimpleGraph(method)
    val stack = ArrayList<SimpleField>()
    var block = graph.startBlock
    val origin = -1

    init {
        // self + parameters
        stack.add(graph.field(classScope.typeWithArgs))
        for (i in parameters.indices) {
            stack.add(graph.field(parameters[i].type))
        }
    }

    override fun visitInsn(opcode: Int) {
        println(OpCode[opcode])
        when (opcode) {
            DUP -> stack.add(stack.last())

            ICONST_0 -> pushConst(Constants.i0)
            ICONST_1 -> pushConst(Constants.i1)
            ICONST_2 -> pushConst(Constants.i2)
            ICONST_3 -> pushConst(Constants.i3)
            ICONST_4 -> pushConst(Constants.i4)

            IADD -> binaryCall(Types.Int, "plus")
            ISUB -> binaryCall(Types.Int, "minus")
            IMUL -> binaryCall(Types.Int, "times")
            IDIV -> binaryCall(Types.Int, "div")
            ISHL -> binaryCall(Types.Int, "shl")
            ISHR -> binaryCall(Types.Int, "shr")
            IUSHR -> binaryCall(Types.Int, "ushr")
            IAND -> binaryCall(Types.Int, "and")
            IOR -> binaryCall(Types.Int, "or")
            IXOR -> binaryCall(Types.Int, "xor")
            INEG -> unaryCall(Types.Int, "negate")

            LADD -> binaryCall(Types.Long, "plus")
            LSUB -> binaryCall(Types.Long, "minus")
            LMUL -> binaryCall(Types.Long, "times")
            LDIV -> binaryCall(Types.Long, "div")
            LSHL -> binaryCall(Types.Long, "shl")
            LSHR -> binaryCall(Types.Long, "shr")
            LUSHR -> binaryCall(Types.Long, "ushr")
            LAND -> binaryCall(Types.Long, "and")
            LOR -> binaryCall(Types.Long, "or")
            LXOR -> binaryCall(Types.Long, "xor")
            LNEG -> unaryCall(Types.Long, "negate")
            LCMP -> binaryCall(Types.Long, "compareTo")

            FADD -> binaryCall(Types.Float, "plus")
            FSUB -> binaryCall(Types.Float, "minus")
            FMUL -> binaryCall(Types.Float, "times")
            FDIV -> binaryCall(Types.Float, "div")
            FREM -> binaryCall(Types.Float, "mod")
            FNEG -> unaryCall(Types.Float, "negate")
            // todo choose the correct variant...
            FCMPL, FCMPG -> binaryCall(Types.Float, "compareTo")

            DADD -> binaryCall(Types.Double, "plus")
            DSUB -> binaryCall(Types.Double, "minus")
            DMUL -> binaryCall(Types.Double, "times")
            DDIV -> binaryCall(Types.Double, "div")
            DREM -> binaryCall(Types.Double, "mod")
            DNEG -> unaryCall(Types.Double, "negate")
            // todo choose the correct variant...
            DCMPL, DCMPG -> binaryCall(Types.Double, "compareTo")

            I2L -> convertCall(Types.Int, Types.Long, "toLong")
            I2F -> convertCall(Types.Int, Types.Float, "toFloat")
            I2D -> convertCall(Types.Int, Types.Double, "toDouble")

            L2I -> convertCall(Types.Long, Types.Int, "toInt")
            L2F -> convertCall(Types.Long, Types.Float, "toFloat")
            L2D -> convertCall(Types.Long, Types.Double, "toDouble")

            F2I -> convertCall(Types.Float, Types.Int, "toInt")
            F2L -> convertCall(Types.Float, Types.Long, "toLong")
            F2D -> convertCall(Types.Float, Types.Double, "toDouble")

            D2I -> convertCall(Types.Double, Types.Int, "toInt")
            D2L -> convertCall(Types.Double, Types.Long, "toLong")
            D2F -> convertCall(Types.Double, Types.Float, "toFloat")

            I2B -> convertCall(Types.Int, Types.Byte, "toByte")
            I2C -> convertCall(Types.Int, Types.Char, "toChar")
            I2S -> convertCall(Types.Int, Types.Short, "toShort")

            else -> TODO("Handle ${OpCode[opcode]}")
        }
    }

    fun convertCall(fromType: ClassType, toType: ClassType, name: String) {
        val p0 = stack.removeLast()
        val dst = graph.field(toType)
        val method = findMethod(fromType.clazz, name)
        block.add(SimpleCall(dst, method, p0, noSpecialization, emptyList(), methodScope, origin))
        stack.add(dst)
    }

    fun unaryCall(type: ClassType, name: String) {
        convertCall(type, type, name)
    }

    fun binaryCall(type: ClassType, name: String) {
        // todo check order
        val p0 = stack.removeLast()
        val p1 = stack.removeLast()
        val dst = graph.field(type)
        val method = findMethod(type.clazz, name, type)
        block.add(SimpleCall(dst, method, p0, noSpecialization, listOf(p1), methodScope, origin))
        stack.add(dst)
    }

    fun equalsParams(expected: Array<out Type>, actual: List<Parameter>): Boolean {
        if (expected.size != actual.size) return false
        for (i in expected.indices) {
            val actualType = actual[i].type.resolvedName
            if (expected[i] != actualType) return false
        }
        return true
    }

    fun findMethod(clazz: Scope, name: String, vararg params: Type): MethodLike {
        return clazz.scope.methods.firstOrNull {
            it.name == name && equalsParams(params, it.valueParameters)
        } ?: throw IllegalStateException("Missing $clazz.$name(${params.joinToString()}), " +
                "candidates: ${clazz.methods}")
    }

    fun pushConst(ne: NumberExpression, type: ClassType = Types.Int) {
        val dst = graph.field(type)
        block.add(SimpleNumber(dst, ne))
        stack.add(dst)
    }

    override fun visitIntInsn(opcode: Int, operand: Int) {
        println("${OpCode[opcode]}($operand)")
        when (opcode) {
            else -> TODO("Handle ${OpCode[opcode]}")
        }
    }

    override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
        println("${OpCode[opcode]}($owner.$name, $descriptor)")
        val fieldType = descToType(descriptor)
        val ownerType = nameToType(owner) as ClassType
        when (opcode) {
            GETFIELD -> {
                // todo check non-null
                val dst = graph.field(fieldType)
                val self = stack.removeLast()
                // todo field can also be in parent class...
                val field = findField(ownerType.clazz, name)
                    ?: throw IllegalStateException("Missing field '$name' in $ownerType")
                val instr = SimpleGetField(dst, self, field, methodScope, origin)
                block.add(instr)
                stack.add(dst)
            }
            PUTFIELD -> {
                // todo check non-null
                // todo check order
                val value = stack.removeLast()
                val self = stack.removeLast()
                println("$self.$name = $value")
                // todo field can also be in parent class...
                val field = findField(ownerType.clazz, name)
                    ?: throw IllegalStateException("Missing field '$name' in $ownerType")
                val instr = SimpleSetField(self, field, value, methodScope, origin)
                block.add(instr)
            }
            else -> TODO("Handle ${OpCode[opcode]}")
        }
    }

    override fun visitVarInsn(opcode: Int, varIndex: Int) {
        // todo load or store a local variable...
        println("visitVarInsn: ${OpCode[opcode]}, $varIndex")
        TODO("Handle")
    }

    fun findField(scope: Scope, name: String): Field? {
        val field = scope.scope.fields.firstOrNull { it.name == name }
        if (field != null) return field

        for (superCall in scope.superCalls) {
            if (superCall.valueParameters != null) {
                return findField(superCall.type.clazz, name)
            }
        }

        return null
    }

    override fun visitIincInsn(varIndex: Int, increment: Int) {
        println("#$varIndex += $increment")
        TODO("Handle")
    }

    override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) {
        println("newMultiArray($descriptor, $numDimensions)")
        TODO("Handle")
    }

    override fun visitLdcInsn(value: Any?) {
        println("loadConst: $value")
        TODO("Handle")
    }

    override fun visitJumpInsn(opcode: Int, label: Label) {
        println("jump(${OpCode[opcode]}) @$label")
        TODO("Handle")
    }

    override fun visitFrame(type: Int, numLocal: Int, local: Array<out Any?>, numStack: Int, stack: Array<out Any?>) {
        println("visitFrame($type, $numLocal, ${local.asList()}, $numStack, ${stack.asList()})")
        TODO("Handle visitFrame")
    }

    override fun visitInvokeDynamicInsn(
        name: String?,
        descriptor: String?,
        bootstrapMethodHandle: Handle?,
        vararg bootstrapMethodArguments: Any?
    ) {
        println("visitInvokeDynamicInsn: $name, $descriptor, $bootstrapMethodHandle, ${bootstrapMethodArguments.asList()}")
        TODO("Handle")
    }

    override fun visitLabel(label: Label) {
        println("visitLabel: $label")
    }

    override fun visitLineNumber(line: Int, start: Label) {
        println("visitLineNumber: $line, start: $start")
    }

    override fun visitMethodInsn(
        opcode: Int,
        owner: String?,
        name: String?,
        descriptor: String?,
        isInterface: Boolean
    ) {
        println("visitMethodInsn: ${OpCode[opcode]}, $owner.$name, descriptor: $descriptor, isInterface: $isInterface")
        TODO("Handle")
    }

    override fun visitLookupSwitchInsn(dflt: Label, keys: IntArray, labels: Array<out Label>) {
        println("visitLookupSwitchInsn: $dflt, ${keys.asList()}, ${labels.asList()}")
        TODO("Handle")
    }

    override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label, vararg labels: Label) {
        println("visitTableSwitchInsn: $min, $max, $dflt, ${labels.asList()}")
        TODO("Handle")
    }

    override fun visitTypeInsn(opcode: Int, type: String?) {
        println("visitTypeInsn: ${OpCode[opcode]}, type: $type")
        TODO("Handle")
    }
}