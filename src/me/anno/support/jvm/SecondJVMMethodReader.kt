package me.anno.support.jvm

import me.anno.support.jvm.FirstJVMClassReader.Companion.API_LEVEL
import me.anno.support.jvm.FirstJVMClassReader.Companion.parseMethodSignature
import me.anno.support.jvm.expression.*
import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.expression.CompareType
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.ast.simple.SimpleThis
import me.anno.zauber.generation.Specializations
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.MatchScore
import me.anno.zauber.typeresolution.members.ResolvedConstructor
import me.anno.zauber.typeresolution.members.ResolvedMember
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.specialization.Specialization.Companion.noSpecialization
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*


/**
 * This just reads the commands and structures.
 * No specialization is applied yet.
 * */
@Suppress("Since15")
class SecondJVMMethodReader(val method: MethodLike, val isStatic: Boolean, parameters: List<Parameter>) :
    MethodVisitor(API_LEVEL) {

    companion object {

        private val LOGGER = LogManager.getLogger(SecondJVMMethodReader::class)

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

    val methodScope get() = method.scope
    val classScope = methodScope.parent!!

    val origin = -1
    val graph = JVMGraph(methodScope, origin)
    val stack = ArrayList<SimpleFieldExpr>()
    var block = graph.startBlock

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
            block.add(JVMSimpleSetField(methodField, tmpField, selfField, methodScope, origin))
            localFields.add(tmpField)
        }
        for (i in parameters.indices) {
            val paramField = parameters[i].getOrCreateField(null, Flags.NONE)
            localFields.add(paramField)
        }
    }

    override fun visitInsn(opcode: Int) {
        LOGGER.debug(OpCode[opcode])
        when (opcode) {

            // duplications
            // no .use() required, because we haven't read them yet
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
                val index = stack.removeLast().use()
                val array = stack.removeLast().use()
                val dst = block.field(baseType)
                val method = findMethod(Types.Array.clazz, "get", Types.Int)
                block.add(JVMSimpleCall(dst, method, array, noSpecialization, listOf(index), methodScope, origin))
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
                val value = stack.removeLast().use()
                val index = stack.removeLast().use()
                val array = stack.removeLast().use()
                val dst = block.field(Types.Unit)
                val method = findMethod(Types.Array.clazz, "set", Types.Int, baseType)
                block.add(
                    JVMSimpleCall(
                        dst,
                        method,
                        array,
                        noSpecialization,
                        listOf(index, value),
                        methodScope,
                        origin
                    )
                )
            }

            ARETURN, IRETURN, LRETURN, FRETURN, DRETURN -> {
                block.add(JVMSimpleReturn(stack.removeLast().use(), methodScope, origin))
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
                    ?: throw IllegalStateException("Missing field 'size' in zauber.Array")
                val instr = JVMSimpleGetField(dst, array, field, methodScope, origin)
                block.add(instr)
                stack.add(dst)
            }


            else -> TODO("Handle ${OpCode[opcode]}")
        }
    }

    fun convertCall(fromType: ClassType, toType: ClassType, name: String) {
        val p0 = stack.removeLast().use()
        val dst = graph.field(toType)
        val method = findMethod(fromType.clazz, name)
        block.add(JVMSimpleCall(dst, method, p0, noSpecialization, emptyList(), methodScope, origin))
        stack.add(dst)
    }

    fun unaryCall(type: ClassType, name: String) {
        convertCall(type, type, name)
    }

    fun binaryCall(type: ClassType, name: String) {
        // todo check order
        val p1 = stack.removeLast().use()
        val p0 = stack.removeLast().use()
        val dst = graph.field(type)
        val method = findMethod(type.clazz, name, type)
        block.add(JVMSimpleCall(dst, method, p0, noSpecialization, listOf(p1), methodScope, origin))
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
        block.add(JVMSimpleCheckEquals(dst, p0, p1, negated, method1, methodScope, origin))
        stack.add(dst)
    }

    fun identicalCall(negated: Boolean) {
        val p1 = stack.removeLast().use()
        val p0 = stack.removeLast().use()
        val dst = graph.field(Types.Boolean)
        block.add(JVMSimpleCheckIdentical(dst, p0, p1, negated, methodScope, origin))
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

    fun pushConst(ne: NumberExpression, type: Type = Types.Int) {
        val dst = graph.field(type)
        block.add(JVMSimpleNumber(dst, ne, methodScope, origin))
        stack.add(dst)
    }

    fun pushNull() {
        val dst = graph.field(NullType)
        block.add(JVMSimpleSpecialValue(dst, SpecialValue.NULL, methodScope, origin))
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
                    else -> throw IllegalStateException("Unexpected operand: $operand")
                }
                val arrayType = Types.Array.withTypeParameter(elementType)
                val size = stack.removeLast().use()
                val dst = block.field(arrayType)
                val tmp = block.field(Types.Unit)
                block.add(JVMSimpleAllocateInstance(dst, arrayType, methodScope, origin))
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
            else -> throw IllegalStateException("Unexpected opcode ${OpCode[opcode]}")
        }
    }

    override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
        LOGGER.debug("${OpCode[opcode]}($owner.$name, $descriptor)")
        val fieldType = descToType(descriptor)
        var ownerType = nameToType(owner) as ClassType
        if (opcode == GETSTATIC || opcode == PUTSTATIC) {
            ownerType = ownerType.clazz
                .getOrPutCompanion().typeWithArgs
        }
        when (opcode) {
            GETSTATIC -> {
                val dst = graph.field(fieldType)
                val self = graph.field(nameToType(owner))
                block.add(JVMSimpleGetObject(self, ownerType.clazz, methodScope, origin))
                val field = findField(ownerType.clazz, name)
                    ?: throw IllegalStateException("Missing field '$name' in $ownerType")
                val instr = JVMSimpleGetField(dst, self, field, methodScope, origin)
                block.add(instr)
                stack.add(dst)
            }
            GETFIELD -> {
                // todo check non-null
                val dst = graph.field(fieldType)
                val self = stack.removeLast().use()
                val field = findField(ownerType.clazz, name)
                    ?: throw IllegalStateException("Missing field '$name' in $ownerType")
                val instr = JVMSimpleGetField(dst, self, field, methodScope, origin)
                block.add(instr)
                stack.add(dst)
            }
            PUTSTATIC -> {
                val dst = graph.field(fieldType)
                val self = graph.field(descToType(owner))
                block.add(JVMSimpleGetObject(self, ownerType.clazz, methodScope, origin))
                val field = findField(ownerType.clazz, name)
                    ?: throw IllegalStateException("Missing field '$name' in $ownerType")
                val instr = JVMSimpleGetField(dst, self, field, methodScope, origin)
                block.add(instr)
                stack.add(dst)
            }
            PUTFIELD -> {
                // todo check non-null
                // todo check order
                val value = stack.removeLast().use()
                val self = stack.removeLast().use()
                LOGGER.debug("$self.$name = $value")
                val field = findField(ownerType.clazz, name)
                    ?: throw IllegalStateException("Missing field '$name' in $ownerType")
                val instr = JVMSimpleSetField(self, field, value, methodScope, origin)
                block.add(instr)
            }
            else -> throw IllegalStateException("Unknown instruction ${OpCode[opcode]}")
        }
    }

    override fun visitVarInsn(opcode: Int, varIndex: Int) {
        // todo load or store a local variable...
        //  first N indices are the parameters incl. self if not static
        LOGGER.debug("visitVarInsn: ${OpCode[opcode]}, $varIndex")

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
                block.add(JVMSimpleGetField(dst, methodField.use(), field, methodScope, origin))
                stack.add(dst)
            }
            ISTORE, LSTORE, FSTORE, DSTORE, ASTORE -> {
                val field = localFields[varIndex]
                val value = stack.removeLast().use()
                block.add(JVMSimpleSetField(methodField.use(), field, value, methodScope, origin))
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
        LOGGER.debug("newMultiArray($descriptor, $numDimensions)")
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
                    else -> throw IllegalStateException()
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
                    else -> throw IllegalStateException()
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
            else -> throw IllegalStateException("Unexpected opcode ${OpCode[opcode]}")
        }

        block.branchCondition = condition
        block.ifBranch = getBlockByLabel(label)
        block.elseBranch = nextBlock
        block = nextBlock
    }

    override fun visitFrame(type: Int, numLocal: Int, local: Array<out Any?>, numStack: Int, stack: Array<out Any?>) {
        check(type == F_NEW)
        LOGGER.debug("visitFrame($type, $numLocal, ${local.asList()}, $numStack, ${stack.asList()})")
        // todo we probably have to adjust the stack, and connect any simple-fields...
    }

    override fun visitInvokeDynamicInsn(
        name: String?,
        descriptor: String?,
        bootstrapMethodHandle: Handle?,
        vararg bootstrapMethodArguments: Any?
    ) {
        LOGGER.debug("visitInvokeDynamicInsn: $name, $descriptor, $bootstrapMethodHandle, ${bootstrapMethodArguments.asList()}")
        TODO("Handle")
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

        // todo resolve method...
        val self: SimpleFieldExpr
        val method: ResolvedMember<*>

        when (opcode) {
            INVOKESPECIAL -> {
                // constructor or super
                when (name) {
                    "<init>" -> {
                        self = stack.removeLast().use()
                        method = resolveConstructor(owner, descriptor, valueParameters)
                    }
                    else -> throw NotImplementedError(name)
                }
            }
            INVOKESTATIC -> {
                // call on companion object
                method = resolveStaticMethod(owner, name, descriptor, typeParameters, valueParameters)
                val objectScope = method.resolved.scope.parent!!
                check(objectScope.scopeType == ScopeType.COMPANION_OBJECT)
                self = block.field(objectScope.typeWithArgs).use()
                block.add(JVMSimpleGetObject(self, objectScope, methodScope, origin))
            }
            INVOKEVIRTUAL, // normal call on potentially open method
            INVOKEINTERFACE -> {
                // interface call
                self = stack.removeLast().use()
                method = resolveDynamicMethod(owner, name, descriptor, typeParameters, valueParameters)
            }
            else -> throw IllegalStateException("Unexpected opcode ${OpCode[opcode]}")
        }

        val dst = block.field(returnType)
        block.add(
            JVMSimpleCall(
                dst, method.resolved, self, method.specialization,
                valueParametersI, methodScope, origin
            )
        )
        stack.add(dst)
    }

    fun resolveStaticMethod(
        owner: String, name: String, descriptor: String,
        typeParameters: List<Parameter>, valueParameters: List<Parameter>,
    ): ResolvedMethod {
        val scope = FirstJVMClassReader.getScope(owner, null).getOrPutCompanion()
        return resolveMethod(scope, owner, name, descriptor, typeParameters, valueParameters)
    }

    fun resolveDynamicMethod(
        owner: String, name: String, descriptor: String,
        typeParameters: List<Parameter>, valueParameters: List<Parameter>,
    ): ResolvedMethod {
        val scope = FirstJVMClassReader.getScope(owner, null)
        return resolveMethod(scope, owner, name, descriptor, typeParameters, valueParameters)
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
        val method = scope.scope.constructors.firstOrNull {
            // equals(typeParameters, it.typeParameters) &&
            equals(valueParameters, it.valueParameters)
        } ?: throw IllegalStateException(
            "Missing constructor ${scope.pathStr}$descriptor -> " +
                    "(${valueParameters.joinToString { it.toString() }}), " +
                    "options: ${
                        scope.constructors
                            .map { "(${valueParameters.joinToString { it.toString() }})" }
                    }"
        )
        return ResolvedConstructor(
            ownerTypes, method,
            ResolutionContext.minimal, scope,
            MatchScore(0)
        )
    }

    fun resolveMethod(
        scope: Scope, owner: String, name: String, descriptor: String,
        typeParameters: List<Parameter>, valueParameters: List<Parameter>
    ): ResolvedMethod {
        val method = scope.methods.firstOrNull {
            it.name == name &&
                    // equals(typeParameters, it.typeParameters) &&
                    equals1(valueParameters, it.valueParameters)
        } ?: throw IllegalStateException(
            "Missing $owner.$name$descriptor -> " +
                    "(${valueParameters.joinToString { it.type.toString() }}), " +
                    "options: ${
                        scope.methods.filter { it.name == name }
                            .map { "(${valueParameters.joinToString { it.type.toString() }})" }
                    }"
        )
        return ResolvedMethod(
            ParameterList.emptyParameterList(), method,
            ParameterList.emptyParameterList(),
            ResolutionContext.minimal, scope,
            MatchScore(0)
        )
    }

    fun equals1(expected: List<Parameter>, actual: List<Parameter>): Boolean {
        if (expected.size != actual.size) return false
        for (i in expected.indices) {
            val expected = expected[i].type
            var actual = actual[i].type
            if (actual is GenericType) {
                actual = actual.superBounds
            }
            if (expected != actual) {
                LOGGER.debug("Mismatch@$i: $expected != $actual")
                return false
            }
        }
        return true
    }

    fun equals(expected: List<Type>, actual: List<Parameter>): Boolean {
        if (expected.size != actual.size) return false
        for (i in expected.indices) {
            val expected = expected[i]
            var actual = actual[i].type
            if (actual is GenericType) {
                actual = actual.superBounds
            }
            if (expected != actual) {
                LOGGER.debug("Mismatch@$i: $expected != $actual")
                return false
            }
        }
        return true
    }

    override fun visitLookupSwitchInsn(dflt: Label, keys: IntArray, labels: Array<out Label>) {
        LOGGER.debug("visitLookupSwitchInsn: $dflt, ${keys.asList()}, ${labels.asList()}")
        TODO("Handle")
    }

    override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label, vararg labels: Label) {
        LOGGER.debug("visitTableSwitchInsn: $min, $max, $dflt, ${labels.asList()}")
        TODO("Handle")
    }

    override fun visitTypeInsn(opcode: Int, type: String) {
        val scope = FirstJVMClassReader.getScope(type, null)
        LOGGER.debug("visitTypeInsn: ${OpCode[opcode]}, type: $type -> $scope")
        when (opcode) {
            NEW -> {
                val type1 = scope.typeWithArgs
                val dst = block.field(type1)
                block.add(JVMSimpleAllocateInstance(dst, type1, methodScope, origin))
                stack.add(dst)
            }
            ANEWARRAY -> TODO("Handle new $scope[]?")
            CHECKCAST -> TODO("Handle checkCast $scope")
            INSTANCEOF -> TODO("Handle instanceOf $scope")
        }
    }

    override fun visitEnd() {
        method.body = graph
    }
}