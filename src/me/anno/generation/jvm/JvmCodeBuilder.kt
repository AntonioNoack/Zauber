package me.anno.generation.jvm

import me.anno.support.jvm.OpCode
import me.anno.utils.ByteArrayOutputStream2
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.arithmetic.NullType
import me.anno.zauber.types.impl.arithmetic.UnionType

/**
 * Tiny JVM bytecode assembler: appends bytes + patches short branch offsets.
 *
 * This is intentionally minimal; we keep maxStack conservative and skip stackmap frames by using classfile major=50.
 */
class JvmCodeBuilder(
    val cp: ConstantPool,
    private val dbgOut: MutableList<String>? = null
) {

    private val out = ByteArrayOutputStream2()

    // label -> pc
    private val labels = HashMap<String, Int>()

    private data class Patch(val opcodePc: Int, val label: String)

    private val patches = ArrayList<Patch>()

    var maxLocals: Int = 0

    private var nextLabelId = 0
    fun newLabel(prefix: String): String = "${prefix}_${nextLabelId++}"

    fun label(name: String) {
        labels[name] = out.size
        dbgOut?.add("$name:")
    }

    fun jump(opcode: Int, label: String) {
        val pc = out.size
        u1(opcode)
        u2(0)
        patches.add(Patch(pc, label))
        dbgOut?.add(OpCode[opcode].uppercase() + " " + label)
    }

    fun goTo(label: String) = jump(Opcodes.GOTO, label)
    fun ifne(label: String) = jump(Opcodes.IFNE, label)

    fun finish(): ByteArray {
        val bytes = out.bytes
        for (p in patches) {
            val target = labels[p.label] ?: error("Unknown label ${p.label}")
            val offset = target - p.opcodePc
            if (offset !in Short.MIN_VALUE.toInt()..Short.MAX_VALUE.toInt()) {
                error("Branch offset out of range: ${p.label} offset=$offset")
            }
            val v = offset and 0xffff
            bytes[p.opcodePc + 1] = (v ushr 8).toByte()
            bytes[p.opcodePc + 2] = v.toByte()
        }
        return out.toByteArray()
    }

    private fun u1(v: Int) {
        out.write(v)
    }

    private fun u2(v: Int) {
        out.write((v ushr 8) and 255)
        out.write(v and 255)
    }

    private fun u4(v: Int) {
        out.write((v ushr 24) and 255)
        out.write((v ushr 16) and 255)
        out.write((v ushr 8) and 255)
        out.write(v and 255)
    }

    private fun dbg(opcode: Int, extra: String? = null) {
        val base = OpCode[opcode].uppercase()
        dbgOut?.add(if (extra != null) "$base $extra" else base)
    }

    // --- helpers ---

    fun aconstNull() = op0(Opcodes.ACONST_NULL)
    fun iconst(v: Int) {
        when (v) {
            -1 -> op0(Opcodes.ICONST_M1)
            0 -> op0(Opcodes.ICONST_0)
            1 -> op0(Opcodes.ICONST_1)
            2 -> op0(Opcodes.ICONST_2)
            3 -> op0(Opcodes.ICONST_3)
            4 -> op0(Opcodes.ICONST_4)
            5 -> op0(Opcodes.ICONST_5)
            in -128..127 -> {
                op1(Opcodes.BIPUSH, v and 255, " $v")
            }
            in -32768..32767 -> {
                u1(Opcodes.SIPUSH); dbg(Opcodes.SIPUSH, v.toString()); u2(v and 0xffff)
            }
            else -> ldcImpl(cp.int(v), v.toString())
        }
    }

    fun fconst(v: Float) {
        when (v) {
            0f -> op0(Opcodes.FCONST_0)
            1f -> op0(Opcodes.FCONST_1)
            2f -> op0(Opcodes.FCONST_2)
            else -> ldcImpl(cp.float(v), v.toString())
        }
    }

    fun ldc(value: String) {
        val idx = cp.string(value)
        ldcImpl(idx, value)
    }

    fun ldcImpl(idx: Int, extra: String) {
        if (idx <= 255) op1(Opcodes.LDC, idx, "#$idx, $extra")
        else {
            u1(Opcodes.LDC_W); u2(idx)
            dbg(Opcodes.LDC_W, "#$idx, $extra")
        }
    }

    fun lconst(v: Long) {
        when (v) {
            0L -> u1(Opcodes.LCONST_0)
            1L -> u1(Opcodes.LCONST_1)
            else -> ldcImpl(cp.long(v), v.toString())
        }
    }

    fun dconst(v: Double) {
        when (v) {
            0.0 -> u1(Opcodes.DCONST_0)
            1.0 -> u1(Opcodes.DCONST_1)
            else -> ldcImpl(cp.double(v), v.toString())
        }
    }

    fun aload0() = op0(Opcodes.ALOAD_0)
    fun iload(slot: Int) = load(JVMValueType.INT, slot)
    fun aload(slot: Int) = load(JVMValueType.REFERENCE, slot)
    fun istore(slot: Int) = store(JVMValueType.INT, slot)
    fun astore(slot: Int) = store(JVMValueType.REFERENCE, slot)

    private fun load(type: JVMValueType, slot: Int) {
        // use _0.._3 when possible
        val op = when (type) {
            JVMValueType.INT -> when (slot) {
                0 -> Opcodes.ILOAD_0
                1 -> Opcodes.ILOAD_1
                2 -> Opcodes.ILOAD_2
                3 -> Opcodes.ILOAD_3
                else -> -1
            }
            JVMValueType.LONG -> when (slot) {
                0 -> Opcodes.LLOAD_0
                1 -> Opcodes.LLOAD_1
                2 -> Opcodes.LLOAD_2
                3 -> Opcodes.LLOAD_3
                else -> -1
            }
            JVMValueType.FLOAT -> when (slot) {
                0 -> Opcodes.FLOAD_0
                1 -> Opcodes.FLOAD_1
                2 -> Opcodes.FLOAD_2
                3 -> Opcodes.FLOAD_3
                else -> -1
            }
            JVMValueType.DOUBLE -> when (slot) {
                0 -> Opcodes.DLOAD_0
                1 -> Opcodes.DLOAD_1
                2 -> Opcodes.DLOAD_2
                3 -> Opcodes.DLOAD_3
                else -> -1
            }
            JVMValueType.REFERENCE -> when (slot) {
                0 -> Opcodes.ALOAD_0
                1 -> Opcodes.ALOAD_1
                2 -> Opcodes.ALOAD_2
                3 -> Opcodes.ALOAD_3
                else -> -1
            }
        }
        if (op >= 0) op0(op)
        else {
            val base = when (type) {
                JVMValueType.INT -> Opcodes.ILOAD
                JVMValueType.LONG -> Opcodes.LLOAD
                JVMValueType.FLOAT -> Opcodes.FLOAD
                JVMValueType.DOUBLE -> Opcodes.DLOAD
                JVMValueType.REFERENCE -> Opcodes.ALOAD
            }
            op1(base, slot, slot.toString())
        }
    }

    private fun store(type: JVMValueType, slot: Int) {
        // use _0.._3 when possible
        val op = when (type) {
            JVMValueType.INT -> when (slot) {
                0 -> Opcodes.ISTORE_0
                1 -> Opcodes.ISTORE_1
                2 -> Opcodes.ISTORE_2
                3 -> Opcodes.ISTORE_3
                else -> -1
            }
            JVMValueType.LONG -> when (slot) {
                0 -> Opcodes.LSTORE_0
                1 -> Opcodes.LSTORE_1
                2 -> Opcodes.LSTORE_2
                3 -> Opcodes.LSTORE_3
                else -> -1
            }
            JVMValueType.FLOAT -> when (slot) {
                0 -> Opcodes.FSTORE_0
                1 -> Opcodes.FSTORE_1
                2 -> Opcodes.FSTORE_2
                3 -> Opcodes.FSTORE_3
                else -> -1
            }
            JVMValueType.DOUBLE -> when (slot) {
                0 -> Opcodes.DSTORE_0
                1 -> Opcodes.DSTORE_1
                2 -> Opcodes.DSTORE_2
                3 -> Opcodes.DSTORE_3
                else -> -1
            }
            JVMValueType.REFERENCE -> when (slot) {
                0 -> Opcodes.ASTORE_0
                1 -> Opcodes.ASTORE_1
                2 -> Opcodes.ASTORE_2
                3 -> Opcodes.ASTORE_3
                else -> -1
            }
        }
        if (op >= 0) op0(op)
        else {
            val base = when (type) {
                JVMValueType.INT -> Opcodes.ISTORE
                JVMValueType.LONG -> Opcodes.LSTORE
                JVMValueType.FLOAT -> Opcodes.FSTORE
                JVMValueType.DOUBLE -> Opcodes.DSTORE
                JVMValueType.REFERENCE -> Opcodes.ASTORE
            }
            op1(base, slot, slot.toString())
        }
    }

    fun pop() = op0(Opcodes.POP)
    fun dup() = op0(Opcodes.DUP)
    fun ret() = op0(Opcodes.RETURN)
    fun areturn() = op0(Opcodes.ARETURN)
    fun ireturn() = op0(Opcodes.IRETURN)
    fun lreturn() = op0(Opcodes.LRETURN)
    fun freturn() = op0(Opcodes.FRETURN)
    fun dreturn() = op0(Opcodes.DRETURN)
    fun athrow() = op0(Opcodes.ATHROW)

    fun iadd() = op0(Opcodes.IADD)
    fun isub() = op0(Opcodes.ISUB)
    fun imul() = op0(Opcodes.IMUL)
    fun idiv() = op0(Opcodes.IDIV)
    fun irem() = op0(Opcodes.IREM)
    fun ixor() = op0(Opcodes.IXOR)

    fun lconst0() = op0(Opcodes.LCONST_0)
    fun fconst0() = op0(Opcodes.FCONST_0)
    fun dconst0() = op0(Opcodes.DCONST_0)

    fun new0(internalName: String) {
        val idx = cp.clazz(internalName)
        u1(Opcodes.NEW); dbg(Opcodes.NEW, internalName); u2(idx)
    }

    fun instanceof0(internalName: String) {
        val idx = cp.clazz(internalName)
        u1(Opcodes.INSTANCEOF); dbg(Opcodes.INSTANCEOF, internalName); u2(idx)
    }

    fun getstatic(owner: String, name: String, desc: String) {
        val idx = cp.fieldRef(owner, name, desc)
        u1(Opcodes.GETSTATIC); dbg(Opcodes.GETSTATIC, "$owner.$name : $desc"); u2(idx)
    }

    fun putstatic(owner: String, name: String, desc: String) {
        val idx = cp.fieldRef(owner, name, desc)
        u1(Opcodes.PUTSTATIC); dbg(Opcodes.PUTSTATIC, "$owner.$name : $desc"); u2(idx)
    }

    fun getfield(owner: String, name: String, desc: String) {
        val idx = cp.fieldRef(owner, name, desc)
        u1(Opcodes.GETFIELD); dbg(Opcodes.GETFIELD, "$owner.$name : $desc"); u2(idx)
    }

    fun putfield(owner: String, name: String, desc: String) {
        val idx = cp.fieldRef(owner, name, desc)
        u1(Opcodes.PUTFIELD); dbg(Opcodes.PUTFIELD, "$owner.$name : $desc"); u2(idx)
    }

    fun invokevirtual(owner: String, name: String, desc: String) {
        val idx = cp.methodRef(owner, name, desc)
        u1(Opcodes.INVOKEVIRTUAL); dbg(Opcodes.INVOKEVIRTUAL, "$owner.$name$desc"); u2(idx)
    }

    fun invokespecial(owner: String, name: String, desc: String) {
        val idx = cp.methodRef(owner, name, desc)
        u1(Opcodes.INVOKESPECIAL); dbg(Opcodes.INVOKESPECIAL, "$owner.$name$desc"); u2(idx)
    }

    fun invokestatic(owner: String, name: String, desc: String) {
        val idx = cp.methodRef(owner, name, desc)
        u1(Opcodes.INVOKESTATIC); dbg(Opcodes.INVOKESTATIC, "$owner.$name$desc"); u2(idx)
    }

    fun invokeinterface(owner: String, name: String, desc: String, argCount: Int) {
        val idx = cp.interfaceMethodRef(owner, name, desc)
        u1(Opcodes.INVOKEINTERFACE); dbg(Opcodes.INVOKEINTERFACE, "$owner.$name$desc")
        u2(idx)
        u1(argCount)
        u1(0)
    }

    fun returnByType(type0: Type) {
        val t = when (type0) {
            is UnionType -> type0.types.first { it != NullType }
            else -> type0
        }
        when (t) {
            Types.Boolean, Types.Byte, Types.Short, Types.Char, Types.Int, Types.UInt -> ireturn()
            Types.Long, Types.ULong -> lreturn()
            Types.Float, Types.Half -> freturn()
            Types.Double -> dreturn()
            else -> areturn()
        }
    }

    fun loadByType(slot: Int, type0: Type) {
        val t = when (type0) {
            is UnionType -> type0.types.first { it != NullType }
            else -> type0
        }
        when (t) {
            Types.Boolean, Types.Byte, Types.Short, Types.Char, Types.Int, Types.UInt -> iload(slot)
            else -> aload(slot)
        }
    }

    /**
     * Appends NEWARRAY/ANEWARRAY for a given element type.
     * For reference types, [refInternalName] must be provided (internal JVM name like "pkg/Foo").
     */
    fun newArray(type: Type, refInternalName: String?) {
        when (type) {
            Types.Boolean -> op1(Opcodes.NEWARRAY, 4, "boolean")
            Types.Char -> op1(Opcodes.NEWARRAY, 5, "char")
            Types.Float, Types.Half -> op1(Opcodes.NEWARRAY, 6, "float")
            Types.Double -> op1(Opcodes.NEWARRAY, 7, "double")
            Types.Byte, Types.UByte -> op1(Opcodes.NEWARRAY, 8, "byte")
            Types.Short, Types.UShort -> op1(Opcodes.NEWARRAY, 9, "short")
            Types.Int, Types.UInt -> op1(Opcodes.NEWARRAY, 10, "int")
            Types.Long, Types.ULong -> op1(Opcodes.NEWARRAY, 11, "long")
            else -> {
                val internal = refInternalName ?: "java/lang/Object"
                val idx = cp.clazz(internal)
                u1(Opcodes.ANEWARRAY); dbg(Opcodes.ANEWARRAY, internal); u2(idx)
            }
        }
    }

    fun arrayLoad(type: Type) {
        when (type) {
            Types.Boolean, Types.Byte, Types.UByte -> op0(Opcodes.BALOAD)
            Types.Short, Types.UShort -> op0(Opcodes.SALOAD)
            Types.Char -> op0(Opcodes.CALOAD)
            Types.Int, Types.UInt -> op0(Opcodes.IALOAD)
            Types.Long, Types.ULong -> op0(Opcodes.LALOAD)
            Types.Float, Types.Half -> op0(Opcodes.FALOAD)
            Types.Double -> op0(Opcodes.DALOAD)
            else -> op0(Opcodes.AALOAD)
        }
    }

    fun arrayStore(type: Type) {
        when (type) {
            Types.Boolean, Types.Byte, Types.UByte -> op0(Opcodes.BASTORE)
            Types.Short, Types.UShort -> op0(Opcodes.SASTORE)
            Types.Char -> op0(Opcodes.CASTORE)
            Types.Int, Types.UInt -> op0(Opcodes.IASTORE)
            Types.Long, Types.ULong -> op0(Opcodes.LASTORE)
            Types.Float, Types.Half -> op0(Opcodes.FASTORE)
            Types.Double -> op0(Opcodes.DASTORE)
            else -> op0(Opcodes.AASTORE)
        }
    }

    private fun op0(opcode: Int) {
        u1(opcode)
        dbg(opcode)
    }

    private fun op1(opcode: Int, b: Int, extra: String? = null) {
        u1(opcode)
        u1(b)
        dbg(opcode, extra?.trim())
    }
}
