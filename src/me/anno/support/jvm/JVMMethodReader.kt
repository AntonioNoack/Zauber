package me.anno.support.jvm

import me.anno.support.jvm.JVMClassReader.Companion.API_LEVEL
import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.expression.CompareType
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.SimpleNode
import me.anno.zauber.ast.simple.SimpleThis
import me.anno.zauber.ast.simple.controlflow.SimpleReturn
import me.anno.zauber.ast.simple.expression.*
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.MatchScore
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.specialization.Specialization.Companion.noSpecialization
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*

@Suppress("Since15")
class JVMMethodReader(val method: MethodLike, val isStatic: Boolean, parameters: List<Parameter>) :
    MethodVisitor(API_LEVEL) {

    companion object {
        class ConstantsImpl {
            val scope = root
            val i0 = NumberExpression("0", scope, -1)
            val i1 = NumberExpression("1", scope, -1)
            val i2 = NumberExpression("2", scope, -1)
            val i3 = NumberExpression("3", scope, -1)
            val i4 = NumberExpression("4", scope, -1)
            val i5 = NumberExpression("5", scope, -1)
            val im1 = NumberExpression("-1", scope, -1)

            val l0 = NumberExpression("0l", scope, -1)
            val l1 = NumberExpression("1l", scope, -1)

            val f0 = NumberExpression("0f", scope, -1)
            val f1 = NumberExpression("1f", scope, -1)
            val f2 = NumberExpression("2f", scope, -1)

            val d0 = NumberExpression("0d", scope, -1)
            val d1 = NumberExpression("1d", scope, -1)

            val NULL = SpecialValueExpression(SpecialValue.NULL, scope, -1)
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

    val localFields = ArrayList<Field>()
    val methodField = graph.field(methodScope.typeWithArgs)

    init {
        graph.thisFields[SimpleThis(methodScope, false)] = methodField

        if (!isStatic) {
            // create self-field and assign it...
            val selfType = classScope.typeWithArgs
            val selfField = graph.field(selfType)
            val tmpField = methodScope.addField(
                null, explicitSelfType = false, isMutable = false, null,
                "__self__", selfType, null,
                Flags.NONE, origin
            )
            graph.thisFields[SimpleThis(classScope, false)] = selfField
            block.add(SimpleSetField(methodField, tmpField, selfField, methodScope, origin))
            localFields.add(tmpField)
        }
        for (i in parameters.indices) {
            val paramField = parameters[i].getOrCreateField(null, Flags.NONE)
            localFields.add(paramField)
        }
    }

    override fun visitInsn(opcode: Int) {
        println(OpCode[opcode])
        when (opcode) {

            // duplications
            DUP -> stack.add(stack.last())
            DUP_X1 -> {
                val v0 = stack.removeLast()
                val v1 = stack.removeLast()
                stack.add(v0)
                stack.add(v1)
                stack.add(v0)
            }

            // constants
            ICONST_0 -> pushConst(Constants.i0)
            ICONST_1 -> pushConst(Constants.i1)
            ICONST_2 -> pushConst(Constants.i2)
            ICONST_3 -> pushConst(Constants.i3)
            ICONST_4 -> pushConst(Constants.i4)
            ICONST_5 -> pushConst(Constants.i5)
            ICONST_M1 -> pushConst(Constants.im1)
            LCONST_0 -> pushConst(Constants.l0)
            LCONST_1 -> pushConst(Constants.l1)
            FCONST_0 -> pushConst(Constants.f0)
            FCONST_1 -> pushConst(Constants.f1)
            FCONST_2 -> pushConst(Constants.f2)
            DCONST_0 -> pushConst(Constants.d0)
            DCONST_1 -> pushConst(Constants.d1)

            ACONST_NULL -> {
                val dst = graph.field(NullType)
                block.add(SimpleSpecialValue(dst, SpecialValue.NULL, methodScope, origin))
                stack.add(dst)
            }

            // math
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

            // number conversions
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

            AALOAD,
            IALOAD, LALOAD, FALOAD, DALOAD,
            BALOAD, SALOAD, CALOAD -> {
                val baseType = when (opcode) {
                    AALOAD -> Types.Any
                    IALOAD -> Types.Int
                    LALOAD -> Types.Long
                    FALOAD -> Types.Float
                    DALOAD -> Types.Double
                    BALOAD -> Types.Byte
                    SALOAD -> Types.Short
                    CALOAD -> Types.Char
                    else -> throw IllegalStateException()
                }
                val index = stack.removeLast()
                val array = stack.removeLast()
                val dst = block.field(baseType)
                val method = findMethod(Types.Array.clazz, "get", Types.Int)
                block.add(SimpleCall(dst, method, array, noSpecialization, listOf(index), methodScope, origin))
                stack.add(dst)
            }

            AASTORE,
            IASTORE, LASTORE, FASTORE, DASTORE,
            BASTORE, SASTORE, CASTORE -> {
                val baseType = when (opcode) {
                    AASTORE -> Types.Any
                    IASTORE -> Types.Int
                    LASTORE -> Types.Long
                    FASTORE -> Types.Float
                    DASTORE -> Types.Double
                    BASTORE -> Types.Byte
                    SASTORE -> Types.Short
                    CASTORE -> Types.Char
                    else -> throw IllegalStateException()
                }
                val value = stack.removeLast()
                val index = stack.removeLast()
                val array = stack.removeLast()
                val dst = block.field(Types.Unit)
                val method = findMethod(Types.Array.clazz, "set", Types.Int, baseType)
                block.add(SimpleCall(dst, method, array, noSpecialization, listOf(index, value), methodScope, origin))
            }

            ARETURN, IRETURN, LRETURN, FRETURN, DRETURN -> {
                block.add(SimpleReturn(stack.removeLast(), methodScope, origin))
            }

            RETURN -> {
                // return unit
                val tmp = graph.field(Types.Unit)
                block.add(SimpleGetObject(tmp, Types.Unit.clazz, methodScope, origin))
                block.add(SimpleReturn(tmp.use(), methodScope, origin))
            }

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
        val p1 = stack.removeLast()
        val p0 = stack.removeLast()
        val dst = graph.field(type)
        val method = findMethod(type.clazz, name, type)
        block.add(SimpleCall(dst, method, p0, noSpecialization, listOf(p1), methodScope, origin))
        stack.add(dst)
    }

    fun equalsCall(type: ClassType, negated: Boolean) {
        val p1 = stack.removeLast()
        val p0 = stack.removeLast()
        val dst = graph.field(Types.Boolean)
        val method = findMethod(type.clazz, "equals", type) as Method
        val method1 = ResolvedMethod(
            ParameterList.emptyParameterList(), method,
            ParameterList.emptyParameterList(), ResolutionContext.minimal,
            methodScope, MatchScore(0)
        )
        block.add(SimpleCheckEquals(dst, p0, p1, negated, method1, methodScope, origin))
        stack.add(dst)
    }

    fun identicalCall(negated: Boolean) {
        val p1 = stack.removeLast()
        val p0 = stack.removeLast()
        val dst = graph.field(Types.Boolean)
        block.add(SimpleCheckIdentical(dst, p0, p1, negated, methodScope, origin))
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
        } ?: throw IllegalStateException(
            "Missing $clazz.$name(${params.joinToString()}), " +
                    "candidates: ${clazz.methods}"
        )
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
        //  first N indices are the parameters incl. self if not static
        println("visitVarInsn: ${OpCode[opcode]}, $varIndex")

        if (localFields.getOrNull(varIndex) == null) {
            check(varIndex == localFields.size) { "Skipped field? $varIndex vs ${localFields.size}" }

            val valueType = when (opcode) {
                ILOAD, ISTORE -> Types.Int
                LLOAD, LSTORE -> Types.Long
                FLOAD, FSTORE -> Types.Float
                DLOAD, DSTORE -> Types.Double
                ALOAD, ASTORE -> Types.Any
                else -> throw IllegalStateException("Unsupported opcode ${OpCode[opcode]}")
            }

            // create self-field and assign it...
            val tmpField = methodScope.addField(
                null, explicitSelfType = false, isMutable = true, null,
                "__tmp${varIndex}__", valueType, null,
                Flags.NONE, origin
            )
            localFields.add(tmpField)
        }

        when (opcode) {
            ILOAD, LLOAD, FLOAD, DLOAD, ALOAD -> {
                val field = localFields[varIndex]
                val valueType = field.valueType!!
                val dst = graph.field(valueType)
                block.add(SimpleGetField(dst, methodField.use(), field, methodScope, origin))
                stack.add(dst)
            }
            ISTORE, LSTORE, FSTORE, DSTORE, ASTORE -> {
                val field = localFields[varIndex]
                val value = stack.removeLast()
                block.add(SimpleSetField(methodField.use(), field, value, methodScope, origin))
            }
            else -> throw IllegalStateException("Unsupported opcode ${OpCode[opcode]}")
        }
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
        visitVarInsn(ILOAD, varIndex)
        pushConst(NumberExpression(increment.toString(), methodScope, origin).apply { resolvedType = Types.Int })
        visitInsn(IADD)
        visitVarInsn(ISTORE, varIndex)
    }

    override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) {
        println("newMultiArray($descriptor, $numDimensions)")
        TODO("Handle")
    }

    override fun visitLdcInsn(value: Any) {
        when (value) {
            is Int -> NumberExpression(value.toString(), methodScope, origin).apply { resolvedType = Types.Int }
            is Long -> NumberExpression(value.toString(), methodScope, origin).apply { resolvedType = Types.Long }
            is Float -> NumberExpression(value.toString(), methodScope, origin).apply { resolvedType = Types.Float }
            is Double -> NumberExpression(value.toString(), methodScope, origin).apply { resolvedType = Types.Double }
            is String -> StringExpression(value, methodScope, origin)
            else -> throw NotImplementedError("Load constant ${value.javaClass.simpleName}")
        }
    }

    val blocksByLabel = HashMap<Label, SimpleNode>()
    fun getBlockByLabel(label: Label): SimpleNode {
        return blocksByLabel.getOrPut(label) { graph.addNode() }
    }

    override fun visitJumpInsn(opcode: Int, label: Label) {
        println("jump(${OpCode[opcode]}) @$label")
        val nextBlock = graph.addNode()
        if (opcode == GOTO) {
            block.nextBranch = nextBlock
            block = nextBlock
            return
        }

        val condition = graph.field(Types.Boolean)
        when (opcode) {
            IFEQ, IFNE -> {
                TODO("Handle compareTo-equals-branch")
            }
            IFLT, IFGE, IFGT, IFLE -> {
                TODO("Handle compareTo-branch")
            }
            IF_ICMPEQ, IF_ICMPNE -> {
                equalsCall(Types.Int, negated = opcode == IF_ICMPNE)
            }
            IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE -> {
                val p1 = stack[stack.size - 1]
                val p0 = stack[stack.size - 2]
                binaryCall(Types.Int, "compareTo")
                val tmp = stack.removeLast()
                val compareType = when (opcode) {
                    IF_ICMPLT -> CompareType.LESS
                    IF_ICMPGT -> CompareType.GREATER
                    IF_ICMPLE -> CompareType.LESS_EQUALS
                    IF_ICMPGE -> CompareType.GREATER_EQUALS
                    else -> throw IllegalStateException()
                }
                block.add(SimpleCompare(condition, p0, p1, compareType, tmp, methodScope, origin))
            }
            IF_ACMPEQ, IF_ACMPNE -> {
                TODO("Handle object-equals-branch")
            }
            IFNULL,
            IFNONNULL -> {
                TODO("Handle object-null branch")
            }
            else -> throw IllegalStateException("Unexpected opcode ${OpCode[opcode]}")
        }

        block.branchCondition = condition
        block.ifBranch = getBlockByLabel(label)
        block.elseBranch = nextBlock
        block = nextBlock
    }

    override fun visitFrame(type: Int, numLocal: Int, local: Array<out Any?>, numStack: Int, stack: Array<out Any?>) {
        check(type == F_NEW)
        println("visitFrame($type, $numLocal, ${local.asList()}, $numStack, ${stack.asList()})")
        // todo we probably have to adjust the stack, and connect any simple-fields...
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
        val newBlock = getBlockByLabel(label)
        block.nextBranch = newBlock
        block = newBlock
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