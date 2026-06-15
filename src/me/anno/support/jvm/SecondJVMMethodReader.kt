package me.anno.support.jvm

import me.anno.generation.Specializations
import me.anno.support.jvm.FirstJVMClassReader.Companion.API_LEVEL
import me.anno.support.jvm.FirstJVMClassReader.Companion.parseMethodSignature
import me.anno.support.jvm.expression.*
import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.utils.StringStyles.GREEN
import me.anno.utils.StringStyles.style
import me.anno.utils.assertEquals
import me.anno.zauber.Zauber.root
import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.ast.rich.expression.CompareType
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.resolved.ThisExpression
import me.anno.zauber.ast.rich.expression.unresolved.FieldExpression
import me.anno.zauber.ast.rich.expression.unresolved.NamedCallExpression
import me.anno.zauber.ast.rich.member.Constructor
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.ast.rich.member.MethodLike
import me.anno.zauber.ast.rich.parameter.NamedParameter
import me.anno.zauber.ast.rich.parameter.Parameter
import me.anno.zauber.ast.rich.parameter.SuperCall
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.MatchScore
import me.anno.zauber.typeresolution.members.ResolvedConstructor
import me.anno.zauber.typeresolution.members.ResolvedMember
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Specialization.Companion.noSpecialization
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType
import me.anno.zauber.types.impl.arithmetic.NullType
import me.anno.zauber.types.impl.arithmetic.UnknownType
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*


/**
 * This just reads the commands and structures.
 * No specialization is applied yet.
 * */
class SecondJVMMethodReader(val method: MethodLike, val isStatic: Boolean, parameters: List<Parameter>) :
    MethodVisitor(API_LEVEL) {

    companion object {

        private val LOGGER = LogManager.getLogger(SecondJVMMethodReader::class)

        private const val INVOKE_LAMBDA = 0

        /**
         * cache for often-used constants, don't want to have to recreate them
         * */
        class ConstantsImpl {
            val scope = root
            val i0 = NumberExpression("0", scope, -1).apply { resolvedType = Types.Int }
            val i1 = NumberExpression("1", scope, -1).apply { resolvedType = Types.Int }
            val i2 = NumberExpression("2", scope, -1).apply { resolvedType = Types.Int }
            val i3 = NumberExpression("3", scope, -1).apply { resolvedType = Types.Int }
            val i4 = NumberExpression("4", scope, -1).apply { resolvedType = Types.Int }
            val i5 = NumberExpression("5", scope, -1).apply { resolvedType = Types.Int }
            val im1 = NumberExpression("-1", scope, -1).apply { resolvedType = Types.Int }

            val l0 = NumberExpression("0l", scope, -1).apply { resolvedType = Types.Long }
            val l1 = NumberExpression("1l", scope, -1).apply { resolvedType = Types.Long }

            val f0 = NumberExpression("0f", scope, -1).apply { resolvedType = Types.Float }
            val f1 = NumberExpression("1f", scope, -1).apply { resolvedType = Types.Float }
            val f2 = NumberExpression("2f", scope, -1).apply { resolvedType = Types.Float }

            val d0 = NumberExpression("0d", scope, -1).apply { resolvedType = Types.Double }
            val d1 = NumberExpression("1d", scope, -1).apply { resolvedType = Types.Double }
        }

        val Constants by threadLocal { ConstantsImpl() }
    }

    init {
        check(Specializations.specialization === noSpecialization)
    }

    fun descToType(desc: String): Type {
        return SignatureReader(desc, methodScope).readType()
    }

    fun nameToType(desc: String): Type {
        // todo can be optimized
        return SignatureReader("L$desc;", methodScope).readType()
    }

    fun nameOrDescToType(desc: String): Type {
        return when {
            desc.endsWith(';') || desc.length <= 2 -> descToType(desc)
            else -> nameToType(desc)
        }
    }

    val methodScope get() = method.scope
    val classScope = methodScope.parent!!

    val origin = -1L
    val graph = JVMGraph(methodScope, isStatic, origin)
    val stack = ArrayList<JVMSimpleField>()
    var block = graph.startBlock

    init {
        val offset = if (isStatic) 0 else 1
        for (i in parameters.indices) {
            graph.getOrPutLocalField(i + offset, parameters[i].name, parameters[i].type)
        }
    }

    override fun visitInsn(opcode: Int) {
        LOGGER.debug(OpCode[opcode])
        when (opcode) {

            // duplications
            // no .use() required, because we haven't read them yet
            POP -> stack.removeLast()
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

            ACONST_NULL -> pushNull()

            // math
            IADD -> binaryCall(Types.Int, "plus")
            ISUB -> binaryCall(Types.Int, "minus")
            IMUL -> binaryCall(Types.Int, "times")
            IDIV -> binaryCall(Types.Int, "div")
            IREM -> binaryCall(Types.Int, "mod")
            ISHL -> binaryCall(Types.Int, "shl")
            ISHR -> binaryCall(Types.Int, "shr")
            IUSHR -> binaryCall(Types.Int, "ushr")
            IAND -> binaryCall(Types.Int, "and")
            IOR -> binaryCall(Types.Int, "or")
            IXOR -> binaryCall(Types.Int, "xor")
            INEG -> unaryCall(Types.Int, "unaryMinus")

            LADD -> binaryCall(Types.Long, "plus")
            LSUB -> binaryCall(Types.Long, "minus")
            LMUL -> binaryCall(Types.Long, "times")
            LDIV -> binaryCall(Types.Long, "div")
            LREM -> binaryCall(Types.Long, "mod")
            LSHL -> binaryCall(Types.Long, "shl", Types.Int)
            LSHR -> binaryCall(Types.Long, "shr", Types.Int)
            LUSHR -> binaryCall(Types.Long, "ushr", Types.Int)
            LAND -> binaryCall(Types.Long, "and")
            LOR -> binaryCall(Types.Long, "or")
            LXOR -> binaryCall(Types.Long, "xor")
            LNEG -> unaryCall(Types.Long, "unaryMinus")
            LCMP -> binaryCall(Types.Long, "compareTo")

            FADD -> binaryCall(Types.Float, "plus")
            FSUB -> binaryCall(Types.Float, "minus")
            FMUL -> binaryCall(Types.Float, "times")
            FDIV -> binaryCall(Types.Float, "div")
            FREM -> binaryCall(Types.Float, "mod")
            FNEG -> unaryCall(Types.Float, "unaryMinus")
            // todo choose the correct variant...
            FCMPL, FCMPG -> binaryCall(Types.Float, "compareTo")

            DADD -> binaryCall(Types.Double, "plus")
            DSUB -> binaryCall(Types.Double, "minus")
            DMUL -> binaryCall(Types.Double, "times")
            DDIV -> binaryCall(Types.Double, "div")
            DREM -> binaryCall(Types.Double, "mod")
            DNEG -> unaryCall(Types.Double, "unaryMinus")
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
                    else -> error("Unreachable")
                }
                val index = stack.removeLast().use()
                val array = stack.removeLast().use()
                val dst = block.field(baseType)
                val method = findMethod(Types.Array.clazz, "get", Types.Int)
                val typeParams = ParameterList(Types.Array.clazz.typeParameters, listOf(baseType))
                val spec = Specialization(method.memberScope, typeParams)
                block.add(
                    JVMSimpleCall(
                        dst, method, array, spec,
                        listOf(index),
                        methodScope, origin
                    )
                )
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
                    else -> error("Unreachable")
                }
                val value = stack.removeLast().use()
                val index = stack.removeLast().use()
                val array = stack.removeLast().use()
                val dst = block.field(Types.Unit)
                val method = findMethod(
                    Types.Array.clazz, "set", Types.Int,
                    GenericType(Types.Array.clazz, Types.Array.clazz.typeParameters[0].name)
                )
                val paramTypes = ParameterList(Types.Array.clazz.typeParameters, listOf(baseType))
                val spec = Specialization(method.memberScope, paramTypes)
                val call = JVMSimpleCall(
                    dst, method, array, spec,
                    listOf(index, value),
                    methodScope, origin
                )
                block.add(call)
            }

            ARETURN, IRETURN, LRETURN, FRETURN, DRETURN -> {
                block.add(JVMSimpleReturn(stack.removeLast().use(), methodScope, origin))
            }

            ATHROW -> {
                val thrown = stack.removeLast().use()
                block.add(JVMSimpleThrow(thrown, methodScope, origin))
            }

            RETURN -> {
                // return unit
                val tmp = graph.field(Types.Unit)
                block.add(JVMSimpleGetObject(tmp, Types.Unit.clazz, methodScope, origin))
                block.add(JVMSimpleReturn(tmp.use(), methodScope, origin))
            }

            ARRAYLENGTH -> {
                val dst = graph.field(Types.Int)
                val array = stack.removeLast().use()
                val field = findField(Types.Array.clazz, "size")
                    ?: error("Missing field 'size' in zauber.Array")
                val instr = JVMSimpleGetClassField(dst, array, field, methodScope, origin)
                block.add(instr)
                stack.add(dst)
            }

            MONITORENTER -> {
                // todo add call...
                stack.removeLast()
            }
            MONITOREXIT -> {
                // todo add call...
                stack.removeLast()
            }

            else -> TODO("Handle ${OpCode[opcode]}")
        }
    }

    fun convertCall(fromType: ClassType, toType: ClassType, name: String) {
        val p0 = stack.removeLast().use()
        val dst = graph.field(toType)
        val method = findMethod(fromType.clazz, name)
        val spec = Specialization.fromSimple(method.memberScope)
        val call = JVMSimpleCall(
            dst, method, p0, spec, emptyList(),
            methodScope, origin
        )
        block.add(call)
        stack.add(dst)
    }

    fun unaryCall(type: ClassType, name: String) {
        convertCall(type, type, name)
    }

    fun binaryCall(type: ClassType, name: String) {
        return binaryCall(type, name, type)
    }

    fun binaryCall(type: ClassType, name: String, argType: Type) {
        // todo check order
        val p1 = stack.removeLast().use()
        val p0 = stack.removeLast().use()
        val dst = graph.field(type)
        val method = findMethod(type.clazz, name, argType)
        block.add(
            JVMSimpleCall(
                dst, method, p0, Specialization.fromSimple(method.memberScope),
                listOf(p1),
                methodScope, origin
            )
        )
        stack.add(dst)
    }

    fun equalsCall(type: ClassType, negated: Boolean) {
        val p1 = stack.removeLast()
        val p0 = stack.removeLast()
        val dst = graph.field(Types.Boolean)
        val method = findMethod(type.clazz, "equals", type) as Method
        val spec = Specialization.fromSimple(method.memberScope)
        val ctx = ResolutionContext.minimal.withSpec(spec)
        val method1 = ResolvedMethod(method, ctx, methodScope, MatchScore.zero)
        block.add(JVMSimpleCheckEquals(dst, p0, p1, negated, method1, methodScope, origin))
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
        val candidates = clazz[ScopeInitType.AFTER_DISCOVERY].methods0
        return candidates.firstOrNull {
            it.name == name && equalsParams(params, it.valueParameters)
        } ?: error(
            "Missing $clazz.$name(${params.joinToString()}), " +
                    "candidates: ${if (candidates.any { it.name == name }) candidates.filter { it.name == name } else candidates}"
        )
    }

    fun pushConst(ne: NumberExpression, type: Type = Types.Int) {
        val dst = graph.field(type)
        block.add(JVMSimpleNumber(dst, ne, methodScope, origin))
        stack.add(dst)
    }

    fun pushNull() {
        val dst = graph.field(NullType)
        block.add(JVMSimpleNull(dst, methodScope, origin))
        stack.add(dst)
    }

    override fun visitIntInsn(opcode: Int, operand: Int) {
        LOGGER.debug("${OpCode[opcode]}($operand)")
        when (opcode) {
            BIPUSH -> {
                val ne = NumberExpression(operand.toString(), methodScope, origin)
                    .apply { resolvedType = Types.Byte }
                pushConst(ne, Types.Byte)
            }
            SIPUSH -> {
                val ne = NumberExpression(operand.toString(), methodScope, origin)
                    .apply { resolvedType = Types.Short }
                pushConst(ne, Types.Short)
            }
            NEWARRAY -> {
                val elementType = when (operand) {
                    T_BOOLEAN -> Types.Boolean
                    T_CHAR -> Types.Char
                    T_FLOAT -> Types.Float
                    T_DOUBLE -> Types.Double
                    T_BYTE -> Types.Byte
                    T_SHORT -> Types.Short
                    T_INT -> Types.Int
                    T_LONG -> Types.Long
                    else -> error("Unexpected operand: $operand")
                }
                createArray(elementType)
            }
            else -> error("Unexpected opcode ${OpCode[opcode]}")
        }
    }

    fun createArray(elementType: Type) {
        val arrayType = Types.Array.withTypeParameter(elementType)
        val size = stack.removeLast().use()
        val dst = block.field(arrayType)
        val tmp = block.field(Types.Unit)
        val allocation = JVMSimpleAllocateInstance(dst, arrayType, methodScope, origin)
        block.add(allocation)
        allocation.valueParameters = listOf(size)
        dst.allocation = allocation
        val constr = resolveConstructor(
            arrayType.clazz, "(Int)",
            ParameterList(arrayType),
            listOf(Types.Int)
        )
        block.add(
            JVMSimpleCall(
                tmp, constr.resolved, dst.use(), constr.specialization,
                listOf(size), methodScope, origin
            )
        )
        stack.add(dst)
    }

    override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
        LOGGER.debug("${OpCode[opcode]}($owner.$name, $descriptor)")
        val fieldType = descToType(descriptor)
        var ownerType = nameToType(owner) as ClassType
        if (opcode == GETSTATIC || opcode == PUTSTATIC) {
            ownerType = ownerType.clazz
                .getOrPutCompanion().typeWithArgs2
        }
        when (opcode) {
            GETSTATIC -> {
                val dst = graph.field(fieldType)
                val self = graph.field(nameToType(owner))
                block.add(JVMSimpleGetObject(self, ownerType.clazz, methodScope, origin))
                val field = findField(ownerType.clazz, name)
                    ?: error("Missing field '$name' in $ownerType")
                val instr = JVMSimpleGetClassField(dst, self, field, methodScope, origin)
                block.add(instr)
                stack.add(dst)
            }
            GETFIELD -> {
                // todo check non-null
                val dst = graph.field(fieldType)
                val self = stack.removeLast().use()
                val field = findField(ownerType.clazz, name)
                    ?: error("Missing field '$name' in $ownerType")
                val instr = JVMSimpleGetClassField(dst, self, field, methodScope, origin)
                block.add(instr)
                stack.add(dst)
            }
            PUTSTATIC -> {
                val value = stack.removeLast().use()
                val self = graph.field(nameToType(owner))
                block.add(JVMSimpleGetObject(self, ownerType.clazz, methodScope, origin))
                val field = findField(ownerType.clazz, name)
                    ?: error("Missing field '$name' in $ownerType")
                val instr = JVMSimpleSetClassField(self, field, value, methodScope, origin)
                block.add(instr)
            }
            PUTFIELD -> {
                // todo check non-null
                // todo check order
                val value = stack.removeLast().use()
                val self = stack.removeLast().use()
                LOGGER.debug("$self.$name = $value")
                val field = findField(ownerType.clazz, name)
                    ?: error("Missing field '$name' in $ownerType")
                val instr = JVMSimpleSetClassField(self, field, value, methodScope, origin)
                block.add(instr)
            }
            else -> error("Unknown instruction ${OpCode[opcode]}")
        }
    }

    override fun visitVarInsn(opcode: Int, varIndex: Int) {

        // load or store a local variable...
        //  first N indices are the parameters incl. self if not static
        LOGGER.debug("visitVarInsn: ${OpCode[opcode]}, $varIndex")

        val valueType = when (opcode) {
            ILOAD, ISTORE -> Types.Int
            LLOAD, LSTORE -> Types.Long
            FLOAD, FSTORE -> Types.Float
            DLOAD, DSTORE -> Types.Double
            ALOAD, ASTORE -> Types.Any
            else -> error("Unsupported opcode ${OpCode[opcode]}")
        }

        val localField = graph.getOrPutLocalField(varIndex, null, valueType)
        when (opcode) {
            ILOAD, LLOAD, FLOAD, DLOAD, ALOAD -> {
                val dst = graph.field(localField.type)
                block.add(JVMSimpleGetLocalField(dst, localField, methodScope, origin))
                stack.add(dst)
            }
            ISTORE, LSTORE, FSTORE, DSTORE, ASTORE -> {
                val value = stack.removeLast().use()
                block.add(JVMSimpleSetLocalField(localField, value, methodScope, origin))
            }
            else -> error("Unsupported opcode ${OpCode[opcode]}")
        }
    }

    fun findField(scope: Scope, name: String): Field? {
        val field = scope[ScopeInitType.AFTER_DISCOVERY].fields
            .firstOrNull { it.name == name }
        if (field != null) return field

        for (superCall in scope.superCalls) {
            if (!superCall.isInterfaceCall) {
                return findField(superCall.type.clazz, name)
            }
        }

        return null
    }

    override fun visitIincInsn(varIndex: Int, increment: Int) {
        visitVarInsn(ILOAD, varIndex)
        visitLdcInsnInt(increment)
        visitInsn(IADD)
        visitVarInsn(ISTORE, varIndex)
    }

    override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) {
        check(descriptor.startsWith('['))
        LOGGER.debug("newMultiArray($descriptor, $numDimensions)")
        val arrayType = descToType(descriptor) as ClassType
        val dst = block.field(arrayType)
        val dimensions = List(numDimensions) { stack.removeLast() }.asReversed()
        block.add(JVMSimpleMultiArray(dst, arrayType, dimensions, methodScope, origin))
        stack.add(dst)
    }

    override fun visitLdcInsn(value: Any) {
        val type = when (value) {
            is Int -> Types.Int
            is Long -> Types.Long
            is Float -> Types.Float
            is Double -> Types.Double
            is String -> {
                val dst = graph.field(Types.String)
                block.add(JVMSimpleString(dst, value, methodScope, origin))
                stack.add(dst)
                return
            }
            is org.objectweb.asm.Type -> {
                val type = nameToType(value.className) as ClassType
                val dst = graph.field(Types.String)
                block.add(JVMSimpleType(dst, type, methodScope, origin))
                stack.add(dst)
                return
            }
            else -> throw NotImplementedError("Load constant ${value.javaClass}")
        }

        val ne = NumberExpression(value.toString(), methodScope, origin)
        ne.resolvedType = type
        val dst = graph.field(type)
        block.add(JVMSimpleNumber(dst, ne, methodScope, origin))
        stack.add(dst)
    }

    fun visitLdcInsnInt(value: Int) {
        val type = Types.Int
        val ne = NumberExpression(value.toString(), methodScope, origin)
        ne.resolvedType = type
        val dst = graph.field(type)
        block.add(JVMSimpleNumber(dst, ne, methodScope, origin))
        stack.add(dst)
    }

    val blocksByLabel = HashMap<Label, JVMBlockExpression>()
    fun getBlockByLabel(label: Label): JVMBlockExpression {
        return blocksByLabel.getOrPut(label) { graph.addNode() }
    }

    override fun visitJumpInsn(opcode: Int, label: Label) {
        LOGGER.debug("jump(${OpCode[opcode]}) @$label")
        val nextBlock = graph.addNode()
        if (opcode == GOTO) {
            block.nextBranch = nextBlock
            block = nextBlock
            return
        }

        val condition = graph.field(Types.Boolean)
        when (opcode) {
            IFEQ, IFNE -> {
                pushConst(Constants.i0)
                equalsCall(Types.Int, negated = opcode == IFNE)
            }
            IFLT, IFGE, IFGT, IFLE -> {
                val tmp = stack.removeLast().use()
                val compareType = when (opcode) {
                    IFLT -> CompareType.LESS
                    IFGT -> CompareType.GREATER
                    IFLE -> CompareType.LESS_EQUALS
                    IFGE -> CompareType.GREATER_EQUALS
                    else -> error("Unreachable")
                }
                block.add(JVMSimpleCompare(condition, null, null, compareType, tmp, methodScope, origin))
            }
            IF_ICMPEQ, IF_ICMPNE -> {
                equalsCall(Types.Int, negated = opcode == IF_ICMPNE)
            }
            IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE -> {
                val p1 = stack[stack.size - 1]
                val p0 = stack[stack.size - 2]
                binaryCall(Types.Int, "compareTo")
                val tmp = stack.removeLast().use()
                val compareType = when (opcode) {
                    IF_ICMPLT -> CompareType.LESS
                    IF_ICMPGT -> CompareType.GREATER
                    IF_ICMPLE -> CompareType.LESS_EQUALS
                    IF_ICMPGE -> CompareType.GREATER_EQUALS
                    else -> error("Unreachable")
                }
                block.add(JVMSimpleCompare(condition, p0, p1, compareType, tmp, methodScope, origin))
            }
            IF_ACMPEQ, IF_ACMPNE -> {
                val p1 = stack.removeLast().use()
                val p0 = stack.removeLast().use()
                val negate = opcode == IF_ACMPNE
                block.add(JVMSimpleCheckIdentical(condition, p0, p1, negate, methodScope, origin))
            }
            IFNULL,
            IFNONNULL -> {
                pushNull()
                val p1 = stack.removeLast().use()
                val p0 = stack.removeLast().use()
                val negate = opcode == IFNONNULL
                block.add(JVMSimpleCheckIdentical(condition, p0, p1, negate, methodScope, origin))
            }
            else -> error("Unexpected opcode ${OpCode[opcode]}")
        }

        block.branchCondition = condition
        block.ifBranch = getBlockByLabel(label)
        block.elseBranch = nextBlock
        block = nextBlock
    }

    override fun visitFrame(type: Int, numLocal: Int, local: Array<out Any?>, numStack: Int, stack1: Array<out Any?>) {
        check(type == F_NEW)
        LOGGER.debug("visitFrame($type, $numLocal, ${local.asList()}, $numStack, ${stack1.asList()})")
        // todo we probably have to adjust the stack, and connect any simple-fields...
        check(block.endStack == null)
        block.endStack = ArrayList(stack)
        stack.clear()

        val newBlock = graph.addNode()
        block.nextBranch = newBlock
        this.block = newBlock

        for (i in stack1.indices.reversed()) {
            val type = when (val type0 = stack1[i]) {
                TOP -> UnknownType // todo what is that???
                INTEGER -> Types.Int
                FLOAT -> Types.Float
                LONG -> Types.Long
                DOUBLE -> Types.Double
                NULL -> NullType
                UNINITIALIZED_THIS -> UnknownType
                null -> UnknownType
                is String -> nameOrDescToType(type0)

                // todo we can lookup this label to find out what object it is: it was allocated there
                is Label -> UnknownType

                else -> throw NotImplementedError("What type is $type0 (${type0.javaClass})?")
            }
            stack.add(newBlock.field(type))
        }
    }

    override fun visitInvokeDynamicInsn(name: String, descriptor: String, method: Handle, vararg args: Any?) {
        if (method.owner == "java/lang/invoke/LambdaMetafactory" && (method.name == "metafactory" || method.name == "altMetafactory") && args.size >= 3) {
            LOGGER.info("visitInvokeDynamicInsn: $name, $descriptor, $method, ${args.asList()}")

            val (typeArgs, valueArgs, interfaceType) = parseMethodSignature(methodScope, descriptor, false)
            val interfaceMethod = findLambdaMethod((interfaceType as ClassType).clazz)
            assertEquals(name, interfaceMethod.name)

            val lambdaScope = classScope.generate(name, ScopeType.NORMAL_CLASS)
            lambdaScope.setEmptyTypeParams()

            // register interface as SuperCall
            lambdaScope.superCalls.add(SuperCall(interfaceType, null, null, origin))

            val constructorScope = lambdaScope.getOrCreatePrimaryConstructorScope()

            val constructorGraph = JVMGraph(constructorScope, false, origin)
            val setters = ArrayList<Expression>()

            // todo check this...
            val lambdaFields = valueArgs.map { param ->
                lambdaScope.addField(
                    null, false, isMutable = false,
                    param, param.name, param.type,
                    null, Flags.SYNTHETIC, origin
                )
            }

            if (valueArgs.isNotEmpty()) {
                val lambdaConstrFields = valueArgs.mapIndexed { index, param ->
                    constructorGraph.getOrPutLocalField(index, param.name, param.type)
                }

                val instanceSelf0 = constructorGraph.thisField!!
                val instanceSelf = constructorGraph.field(instanceSelf0.type)
                val constrBlock = constructorGraph.startBlock
                constrBlock.add(
                    JVMSimpleGetLocalField(
                        instanceSelf,
                        instanceSelf0, constructorScope, origin
                    )
                )

                for (i in lambdaFields.indices) {
                    val dst = constructorGraph.field(valueArgs[i].type)
                    constrBlock.add(
                        JVMSimpleGetLocalField(
                            dst,
                            lambdaConstrFields[i],
                            constructorScope,
                            origin
                        )
                    )
                    constrBlock.add(
                        JVMSimpleSetClassField(
                            instanceSelf,
                            lambdaFields[i],
                            dst,
                            constructorScope,
                            origin
                        )
                    )
                }
            }

            constructorScope.selfAsConstructor = Constructor(
                valueArgs, constructorScope,
                null, ExpressionList(setters, constructorScope, origin), Flags.SYNTHETIC, origin
            )

            val dst1 = args[1] as Handle
            val ownerType = nameToType(dst1.owner) as ClassType
            val staticScope = ownerType.clazz.getOrPutCompanion()

            val lambdaMethodScope = lambdaScope.getOrPut(interfaceMethod.name, ScopeType.METHOD)
            val lambdaMethod = Method(
                null, false, interfaceMethod.name,
                interfaceMethod.typeParameters.map { it.withScope(lambdaMethodScope) },
                interfaceMethod.valueParameters.map { it.withScope(lambdaMethodScope) },
                lambdaMethodScope, interfaceMethod.returnType,
                emptyList(), null, Flags.NONE, origin
            )
            lambdaMethodScope.selfAsMethod = lambdaMethod

            val joinedValueParams = lambdaFields.map { field ->
                FieldExpression(field, lambdaScope, origin)
            } + lambdaMethod.valueParameters.map { param ->
                FieldExpression(param.getOrCreateField(null, Flags.SYNTHETIC), lambdaMethodScope, origin)
            }
            val lambdaCallExpr = NamedCallExpression(
                ThisExpression(staticScope, methodScope, origin),
                interfaceMethod.name, emptyList(),
                emptyList(), joinedValueParams.map { NamedParameter(null, it) },
                methodScope, origin
            )
            lambdaMethod.body = ReturnExpression(lambdaCallExpr, null, methodScope, origin)

            val valueArgs2 = valueArgs
                .map { stack.removeLast().use() }
                .asReversed()

            val dst = block.field(interfaceType)
            val unit = block.field(Types.Unit)
            block.add(JVMSimpleAllocateInstance(dst, lambdaScope.typeWithArgs2, methodScope, origin))
            block.add(
                JVMSimpleCall(
                    unit, constructorScope.selfAsConstructor!!, dst,
                    Specialization.fromSimple(constructorScope), valueArgs2, methodScope, origin
                )
            )
            stack.add(dst)

        } else {
            throw NotImplementedError("Unknown lambda type: $method, ${args.toList()}")
        }
    }

    fun findLambdaMethod(interfaceScope: Scope): Method {
        interfaceScope[ScopeInitType.AFTER_OVERRIDES]
        val candidates = interfaceScope.methods0.filter { it.isAbstract() }
        check(candidates.size == 1)
        return candidates.first()
    }

    override fun visitLabel(label: Label) {
        val newBlock = getBlockByLabel(label)
        block.nextBranch = newBlock
        block = newBlock
    }

    override fun visitLineNumber(line: Int, start: Label) {
        LOGGER.debug("visitLineNumber: $line, start: $start")
    }

    override fun visitMethodInsn(
        opcode: Int, owner: String, name: String,
        descriptor: String, isInterface: Boolean
    ) {
        // what does the isInterface flag say? if the method's owner class is an interface.
        val (typeParameters, valueParameters, returnType) = parseMethodSignature(methodScope, descriptor, false)
        LOGGER.debug(
            "visitMethodInsn: ${OpCode[opcode]}, " +
                    "$owner.$name<${typeParameters.joinToString()}>(${valueParameters.joinToString()}): $returnType"
        )

        val valueParametersI = valueParameters
            .map { stack.removeLast() }
            .asReversed()

        // resolve method
        val self: JVMSimpleField
        val method: ResolvedMember<*>

        when (opcode) {
            INVOKESPECIAL -> {
                // constructor or super
                when (name) {
                    "<init>" -> {
                        self = stack.removeLast().use()
                        method = resolveConstructor(owner, descriptor, valueParameters)
                    }
                    else -> {
                        self = stack.removeLast().use()
                        val scope = FirstJVMClassReader.getScope(owner, null)
                        method = resolveMethod(scope, name, descriptor, typeParameters, valueParameters, true)
                    }
                }
            }
            INVOKESTATIC -> {
                // call on companion object
                // println("Resolving static method $owner.$name$descriptor")
                method = resolveStaticMethod(owner, name, descriptor, typeParameters, valueParameters)
                val objectScope = method.resolved.scope.parent!!
                check(objectScope.scopeType == ScopeType.COMPANION_OBJECT)
                self = block.field(objectScope.typeWithArgs).use()
                block.add(JVMSimpleGetObject(self, objectScope, methodScope, origin))
            }
            INVOKEVIRTUAL, // normal call on potentially open method
            INVOKEINTERFACE,
            INVOKE_LAMBDA -> {
                // interface call
                self = stack.removeLast().use()
                method = resolveDynamicMethod(owner, name, descriptor, typeParameters, valueParameters)
            }
            else -> error("Unexpected opcode ${OpCode[opcode]}")
        }

        val dst = block.field(returnType)
        block.add(
            JVMSimpleCall(
                dst, method.resolved, self, method.specialization,
                valueParametersI, methodScope, origin
            )
        )
        if (returnType != Types.Unit) {
            stack.add(dst)
        }
    }

    fun resolveStaticMethod(
        owner: String, name: String, descriptor: String,
        typeParameters: List<Parameter>, valueParameters: List<Parameter>,
    ): ResolvedMethod {
        val scope = FirstJVMClassReader.getScope(owner, null).getOrPutCompanion()
        return resolveMethod(scope, name, descriptor, typeParameters, valueParameters, false)
    }

    fun resolveDynamicMethod(
        owner: String, name: String, descriptor: String,
        typeParameters: List<Parameter>, valueParameters: List<Parameter>,
    ): ResolvedMethod {
        val ownerType = if (owner.startsWith('[') || owner.endsWith(';')) {
            descToType(owner)
        } else {
            nameToType(owner)
        }
        val scope = (ownerType as ClassType).clazz
        return resolveMethod(scope, name, descriptor, typeParameters, valueParameters, true)
    }

    fun resolveConstructor(
        owner: String, descriptor: String,
        valueParameters: List<Parameter>,
    ): ResolvedConstructor {
        val scope = FirstJVMClassReader.getScope(owner, null)
        return resolveConstructor1(scope, descriptor, valueParameters)
    }

    fun resolveConstructor1(
        scope: Scope, descriptor: String,
        valueParameters: List<Parameter>,
    ): ResolvedConstructor {
        return resolveConstructor(
            scope, descriptor,
            ParameterList.emptyParameterList(),
            valueParameters.map { it.type })
    }

    fun resolveConstructor(
        scope: Scope, descriptor: String,
        ownerTypes: ParameterList,
        valueParameters: List<Type>,
    ): ResolvedConstructor {
        // println("Resolving constructor ${scope.pathStr}$descriptor")
        val method = scope[ScopeInitType.AFTER_DISCOVERY]
            .constructors0
            .firstOrNull {
                // equals(typeParameters, it.typeParameters) && // checking valueParams is sufficient, and saves us work
                equals(valueParameters, it.valueParameters)
            } ?: error(
            "Missing constructor $scope<$ownerTypes>$descriptor -> " +
                    "(${valueParameters.joinToString { it.toString() }}), " +
                    "options: ${
                        scope.constructors0
                            .map { "(${valueParameters.joinToString { it.toString() }})" }
                    }"
        )
        val spec = Specialization(ClassType(scope, ownerTypes))
            .withScope(method.memberScope)
        val ctx = ResolutionContext(
            null, spec, true, null,
            emptyMap(), emptyList()
        )
        return ResolvedConstructor(method, ctx, methodScope, MatchScore.zero)
    }

    fun resolveMethod(
        scope0: Scope, name: String, descriptor: String,
        typeParameters: List<Parameter>, valueParameters: List<Parameter>,
        allowSupScopes: Boolean
    ): ResolvedMethod {
        var scope = scope0
        while (true) {
            val method = scope[ScopeInitType.AFTER_DISCOVERY]
                .methods0.firstOrNull {
                    it.name == name &&
                            // equals(typeParameters, it.typeParameters) && // checking valueParams is sufficient, saves work
                            equals1(valueParameters, it.valueParameters)
                }
            if (method != null) {
                return resolveMethodI(scope, typeParameters, method)
            }

            if (!allowSupScopes) break
            // check super classes
            scope = scope.superCalls
                .firstOrNull { it.isClassCall }
                ?.type?.clazz
                ?: break
        }

        error(
            "Missing $scope0.$name, $descriptor -> " +
                    "(${valueParameters.joinToString { it.type.toString() }}), " +
                    "options: ${
                        scope0.methods0.filter { it.name == name }
                            .map { "(${it.valueParameters.joinToString { vp -> vp.type.toString() }})" }
                    }, ${scope0.methods0.map { style("\"${it.name}\"", GREEN) }.distinct().sorted()}"
        )
    }

    fun resolveMethodI(scope: Scope, typeParameters: List<Parameter>, method: Method): ResolvedMethod {
        val typeParams = ParameterList(
            scope.typeParameters,
            typeParameters.map { it.type }.ifEmpty { scope.typeParameters.map { it.type } }
        )
        val typeParamsI = typeParams.ifEmpty {
            ParameterList(
                method.memberScope.typeParameters,
                method.memberScope.typeParameters.map { it.type })
        }
        val spec = Specialization(method.memberScope, typeParamsI)
        val ctx = ResolutionContext(
            null, spec, true, null,
            emptyMap(), emptyList()
        )
        return ResolvedMethod(method, ctx, methodScope, MatchScore.zero)
    }

    fun equals1(expected: List<Parameter>, actual: List<Parameter>): Boolean {
        if (expected.size != actual.size) {
            // println("Size-Mismatch: $expected != $actual")
            return false
        }
        for (i in expected.indices) {
            var expected = expected[i].type.resolvedName
            var actual = actual[i].type.resolvedName
            if (actual is GenericType) actual = actual.superBounds
            if (expected is ClassType && expected.typeParameters != null) expected = expected.withTypeParameters(null)
            if (actual is ClassType && actual.typeParameters != null) actual = actual.withTypeParameters(null)
            if (expected != actual) {
                // println("Mismatch@$i: $expected != $actual (${expected.javaClass.simpleName} vs ${actual.javaClass.simpleName})")
                return false
            }
        }
        return true
    }

    fun equals(expected: List<Type>, actual: List<Parameter>): Boolean {
        if (expected.size != actual.size) {
            return false
        }
        for (i in expected.indices) {
            val expected = expected[i].resolvedName
            var actual = actual[i].type.resolvedName
            if (actual is GenericType) {
                actual = actual.superBounds
            }
            if (!equals(expected, actual)) {
                // LOGGER.info("Mismatch@$i: $expected != $actual")
                return false
            }
        }
        return true
    }

    fun equals(expected: Type, actual: Type): Boolean {
        if (expected is ClassType && actual is ClassType) {
            // generic-specifics aren't supported in Java, so we can just skip them
            return expected.clazz == actual.clazz
            /*if (expected.clazz != actual.clazz) return false
            val ts = expected.clazz.typeParameters.size
            val ep = expected.typeParameters ?: List(ts) { UnknownType }
            val ap = actual.typeParameters ?: List(ts) { UnknownType }
            assertEquals(ep.size, ap.size) {
                "Parameter mismatch in $expected =?= $actual"
            }
            return ep.indices.all {
                equals(ep[it], ap[it])
            }*/
        }
        if (expected is ClassType && actual is GenericType) {
            return actual.superBounds == expected
        }
        return expected == actual
    }

    override fun visitLookupSwitchInsn(defaultLabel: Label, keys: IntArray, labels: Array<out Label>) {
        if (LOGGER.isDebugEnabled) LOGGER.debug("visitLookupSwitchInsn: $defaultLabel, ${keys.asList()}, ${labels.asList()}")
        check(keys.size == labels.size)

        for (i in keys.indices) {
            visitInsn(DUP)
            visitLdcInsnInt(keys[i])
            visitJumpInsn(IF_ICMPEQ, labels[i])
        }
        visitJumpInsn(GOTO, defaultLabel)
    }

    override fun visitTableSwitchInsn(min: Int, max: Int, defaultLabel: Label, vararg labels: Label) {
        if (LOGGER.isDebugEnabled) LOGGER.debug("visitTableSwitchInsn: $min, $max, $defaultLabel, ${labels.asList()}")
        check(labels.size == max - min + 1)

        for (i in labels.indices) {
            visitInsn(DUP)
            visitLdcInsnInt(min + i)
            visitJumpInsn(IF_ICMPEQ, labels[i])
        }
        visitJumpInsn(GOTO, defaultLabel)
    }

    override fun visitTypeInsn(opcode: Int, type: String) {
        val type1 = if (type.startsWith('[') || type.endsWith(';')) {
            descToType(type)
        } else {
            nameToType(type)
        }

        val scope = (type1 as ClassType).clazz
        LOGGER.debug("visitTypeInsn: ${OpCode[opcode]}, type: $type -> $scope")
        when (opcode) {
            NEW -> {
                val dst = block.field(type1)
                val allocation = JVMSimpleAllocateInstance(dst, type1, methodScope, origin)
                block.add(allocation)
                dst.allocation = allocation
                stack.add(dst)
            }
            ANEWARRAY -> {
                createArray(type1)
            }
            CHECKCAST -> {
                val value = stack.removeLast().use()
                block.add(JVMSimpleCheckCast(value, type1, methodScope, origin))
                stack.add(value)
            }
            INSTANCEOF -> {
                val value = stack.removeLast().use()
                val dst = block.field(Types.Boolean)
                block.add(JVMSimpleInstanceOf(dst, value, type1, methodScope, origin))
                stack.add(dst)
            }
        }
    }

    fun setAllFieldsToNull() {
        val fields = method.ownerScope.fields
        if (fields.isNotEmpty()) {
            val nulls = HashMap<Type, JVMSimpleField>()
            val block = graph.startBlock

            val self = if(isStatic) {
                val selfScope = method.ownerScope
                val self = block.field(selfScope.typeWithArgs)
                block.add(JVMSimpleGetObject(self, selfScope, methodScope, origin))
                self
            } else {
                val self0 = graph.thisField!!
                val self = block.field(self0.type)
                block.add(JVMSimpleGetLocalField(self, self0, methodScope, origin))
                self
            }

            for (field in fields) {
                val fieldType = field.resolveValueType(ResolutionContext.minimal)
                val value = nulls.getOrPut(fieldType) {
                    val tmp = block.field(fieldType)
                    block.add(JVMSimpleNull(tmp, methodScope, origin))
                    tmp
                }
                block.add(JVMSimpleSetClassField(self, field, value, methodScope, origin))
            }
        }
    }

    override fun visitEnd() {

        if (method is Constructor) {
            setAllFieldsToNull()
        }

        method.body = graph
    }
}