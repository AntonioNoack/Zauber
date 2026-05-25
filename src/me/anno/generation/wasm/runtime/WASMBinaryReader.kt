package me.anno.generation.wasm.runtime

import me.anno.generation.wasm.*
import me.anno.utils.assertEquals
import me.anno.zauber.logging.LogManager
import java.io.EOFException

class WASMBinaryReader(
    val data: ByteArray,
    var pos: Int = 0
) {

    companion object {
        private val LOGGER = LogManager.getLogger(WASMBinaryReader::class)
    }

    var limit = data.size
    val binary = WASMBinary()

    fun read(): WASMBinary {
        readModuleHeader()
        while (pos < limit) {
            val (id, size) = readSectionHeader()
            check(pos + size <= limit) { "Section too big for file: $pos + $size > $limit" }
            check(size >= 0) { "Section cannot have negative size" }
            limit = pos + size
            // println("Reading section $id, $pos += $size")
            when (WASMSection.entries[id]) {
                WASMSection.TYPE -> binary.types = readTypeSection()
                WASMSection.IMPORT -> binary.imports = readImportSection()
                WASMSection.FUNCTION -> binary.functions = readFunctionSection()
                // table
                WASMSection.MEMORY -> binary.numPages = readMemorySection()
                WASMSection.GLOBAL -> binary.globals = readGlobalSection()
                WASMSection.EXPORT -> binary.exports = readExportSection().toMap()
                WASMSection.CODE -> binary.code = readCodeSection()
                else -> LOGGER.warn("Unknown section header: $id")
            }
            pos = limit
            limit = data.size
        }
        return binary
    }

    fun u8(): Int {
        if (pos >= limit) throw EOFException()
        return data[pos++].toInt() and 0xff
    }

    fun u32(): Int {
        val v = readVarUInt()
        if (v > Int.MAX_VALUE) {
            throw IllegalArgumentException("u32 too large: $v")
        }
        return v.toInt()
    }

    fun f32(): Float {
        return Float.fromBits(le32())
    }

    fun f64(): Double {
        val lo = le32().toLong() and 0xffffffffL
        val hi = le32().toLong() and 0xffffffffL
        return Double.fromBits(lo or (hi shl 32))
    }

    fun le32(): Int {
        val b0 = u8()
        val b1 = u8()
        val b2 = u8()
        val b3 = u8()
        return b0 or
                (b1 shl 8) or
                (b2 shl 16) or
                (b3 shl 24)
    }

    // unsigned LEB128
    fun readVarUInt(): Long {
        var result = 0L
        var shift = 0

        while (true) {
            val byte = u8()

            result = result or ((byte and 0x7f).toLong() shl shift)

            if ((byte and 0x80) == 0) {
                return result
            }

            shift += 7

            if (shift > 63) {
                throw IllegalArgumentException("Invalid VarUInt")
            }
        }
    }

    // signed LEB128
    fun readVarInt(): Long {
        var result = 0L
        var shift = 0
        var byte: Int

        while (true) {
            byte = u8()

            result = result or ((byte and 0x7f).toLong() shl shift)
            shift += 7

            if ((byte and 0x80) == 0) {
                break
            }

            if (shift > 63) {
                throw IllegalArgumentException("Invalid VarInt")
            }
        }

        // sign extension
        if (shift < 64 && (byte and 0x40) != 0) {
            result = result or (-1L shl shift)
        }

        return result
    }

    fun readModuleHeader() {
        val magic = le32()
        if (magic != 0x6D736100) {
            throw IllegalArgumentException("Invalid WASM magic: ${magic.toString(16)}")
        }

        val version = le32()
        if (version != 1) {
            throw IllegalArgumentException("Unsupported WASM version: $version")
        }
    }

    fun readSectionHeader(): Pair<Int, Int> {
        val sectionId = u8()
        val sectionSize = u32()
        return sectionId to sectionSize
    }

    fun readFunctionSection(): List<FunctionType> {
        return List(u32()) {
            binary.types[u32()] as FunctionType
        }
    }

    fun readTypeSection(): List<WASMFuncTypeOrStruct> {

        val count = u32()
        val result = ArrayList<WASMFuncTypeOrStruct>(count)

        repeat(count) {
            when (val kind = u8()) {
                0x50 -> {
                    // subtype

                    val superTypeCount = u32()
                    check(superTypeCount in 0..1) { "Too many super-types" }
                    val superType =
                        if (superTypeCount > 0) u32()
                        else null

                    when (val structKind = u8()) {
                        0x5f -> {
                            val fields = List(u32()) {
                                val type = readValueType()

                                val mutable = u8()
                                if (mutable != 0x01) {
                                    throw IllegalArgumentException("Expected mutable field")
                                }

                                type
                            }

                            val s = WASMStruct(
                                typeIndex = result.size,
                                typeName = "type${result.size}",
                                superType = superType?.let {
                                    result[it] as WASMStruct
                                },
                                isNullable = false // todo fix this, where do we get the info from?
                            )
                            for (index in fields.indices) {
                                val type = fields[index]
                                s.properties.add(WASMProperty(null, type, index))
                            }
                            result += s
                        }
                        0x5e -> {
                            val type = readValueType()

                            val mutable = u8()
                            if (mutable != 0x01) {
                                throw IllegalArgumentException("Expected mutable field")
                            }

                            val s = WASMArray(
                                superType = superType?.let { result[it] as WASMStructLike },
                                typeIndex = result.size,
                                typeName = "type${result.size}",
                                elementStruct = null, // type as? WASMStruct,
                                elementType = type,
                                isNullable = false // todo fix this, where do we get the info from?
                            )
                            result += s
                        }
                        else -> {
                            throw IllegalArgumentException("Unexpected struct type 0x${structKind.toString(16)}")
                        }
                    }
                }
                0x60 -> {
                    // function type

                    val paramCount = u32()
                    val params = List(paramCount) {
                        readValueType()
                    }

                    val resultCount = u32()
                    val results = List(resultCount) {
                        readValueType()
                    }

                    result += FunctionType(params, results)
                }
                else -> throw IllegalArgumentException("Unknown type kind: $kind")
            }
        }

        return result
    }

    fun readMemorySection(): Int {

        val memoryCount = u32()
        assertEquals(1, memoryCount, "Expected exactly one memory")

        val flags = u8()
        assertEquals(0x00, flags, "Unsupported memory flags")

        return u32()
    }

    fun readImportSection(): List<Pair<String, FunctionType>> {
        return List(u32()) {

            val module = readName()
            if (module != "env") {
                throw IllegalArgumentException("Unsupported module: $module")
            }

            val name = readName()

            val kind = u8()
            if (kind != 0x00) {
                throw IllegalArgumentException("Only function imports supported")
            }

            val typeIndex = u32()
            name to (binary.types[typeIndex] as FunctionType)
        }
    }

    fun readExportSection(): List<Pair<String, Int>> {
        return List(u32()) {

            val name = readName()

            val kind = u8()
            if (kind != 0x00) {
                throw IllegalArgumentException("Only function exports supported")
            }

            val index = u32() // function index, I believe
            name to index
        }
    }

    fun readGlobalSection(): List<WASMType.Ref> {
        return List(u32()) {

            val type = readValueType() as? WASMType.Ref
                ?: throw IllegalArgumentException("Expected ref type")

            val mutable = u8()
            if (mutable != 0x01) {
                throw IllegalArgumentException("Expected mutable global")
            }

            consumeOpcode(WASMOpcode.REF_NULL)

            val heapType = readHeapTypeOnly()
            if (heapType != type.typeIndex) {
                throw IllegalArgumentException("Mismatching heap type")
            }

            consumeOpcode(WASMOpcode.END)

            type
        }
    }

    fun readCodeSection(): List<WASMMethod> {
        val count = u32()
        val sectionLimit = limit
        // println("Reading $count implementations")
        return List(count) {
            val size = u32()
            check(size >= 0)

            val codeLimit = pos + size
            check(codeLimit <= data.size)
            limit = codeLimit

            val numLocals = u32()
            val locals = List(numLocals) {
                val repetitions = u32()
                val type = readValueType()
                List(repetitions) { type }
            }.flatten()

            val instructions = ArrayList<WASMInstruction>(size)
            while (pos < codeLimit) {
                instructions += readInstruction()
            }

            pos = codeLimit
            limit = sectionLimit
            WASMMethod(locals, instructions)
        }
    }

    fun readHeapTypeOnly(): Int {
        return readVarInt().toInt()
    }

    fun readValueType(): WASMType {
        return when (val id = u8()) {
            0x7f -> WASMType.I32
            0x7e -> WASMType.I64
            0x7d -> WASMType.F32
            0x7c -> WASMType.F64

            0x78 -> WASMType.I8
            0x77 -> WASMType.I16

            0x63 -> {
                val typeIndex = readVarInt().toInt()
                WASMType.Ref(typeIndex, "type$typeIndex", true)
            }

            0x64 -> {
                val typeIndex = readVarInt().toInt()
                WASMType.Ref(typeIndex, "type$typeIndex", false)
            }

            else -> throw IllegalArgumentException("Unknown value type: $id")
        }
    }

    fun readName(): String {
        val length = u32()
        val chars = CharArray(length)

        for (i in 0 until length) {
            chars[i] = u8().toChar()
        }

        return String(chars)
    }

    fun consumeOpcode(expected: Int) {
        val actual = u8()
        if (actual != expected) {
            throw IllegalArgumentException(
                "Expected opcode $expected, got $actual"
            )
        }
    }

    private fun readBlockBody(): List<WASMInstruction> {
        val body = ArrayList<WASMInstruction>()
        while (true) {
            val instr = readInstruction()
            if (instr == WASMInstruction.End) break
            body.add(instr)
        }
        return body
    }

    @Suppress("Since15")
    fun readInstruction(): WASMInstruction {
        return when (val opcode = u8()) {

            WASMOpcode.UNREACHABLE -> WASMInstruction.Unreachable
            WASMOpcode.NOP -> WASMInstruction.Nop

            WASMOpcode.BLOCK -> {
                val type = u8()
                assertEquals(0x40, type, "Unexpected block-type")
                WASMInstruction.Block(type, readBlockBody())
            }

            WASMOpcode.LOOP -> {
                val type = u8()
                assertEquals(0x40, type, "Unexpected loop-type")
                WASMInstruction.Loop(type, readBlockBody())
            }

            WASMOpcode.IF -> {
                val type = u8()
                assertEquals(0x40, type, "Unexpected if-type")

                val ifBranch = ArrayList<WASMInstruction>()
                while (true) {
                    val instr = readInstruction()
                    ifBranch.add(instr)
                    if (instr == WASMInstruction.Else || instr == WASMInstruction.End) break
                }
                val last = ifBranch.removeLast()

                val elseBranch = if (last == WASMInstruction.Else) {
                    readBlockBody()
                } else emptyList()

                WASMInstruction.If(type, ifBranch, elseBranch)
            }

            WASMOpcode.ELSE -> WASMInstruction.Else
            WASMOpcode.END -> WASMInstruction.End
            WASMOpcode.BR -> {
                val depth = u32()
                WASMInstruction.br[depth]
            }
            WASMOpcode.BR_TABLE -> {
                val numTargets = u32()
                val targets = IntArray(numTargets + 1) { u32() }
                WASMInstruction.BrTable(targets)
            }

            WASMOpcode.RETURN -> WASMInstruction.Return

            WASMOpcode.CALL -> WASMInstruction.Call(u32())
            WASMOpcode.CALL_INDIRECT -> WASMInstruction.CallIndirect(u32())

            WASMOpcode.LOCAL_GET -> WASMInstruction.LocalGet(u32())
            WASMOpcode.LOCAL_SET -> WASMInstruction.LocalSet(u32())
            WASMOpcode.LOCAL_TEE -> WASMInstruction.LocalTee(u32())

            WASMOpcode.GLOBAL_GET -> WASMInstruction.GlobalGet(u32())
            WASMOpcode.GLOBAL_SET -> WASMInstruction.GlobalSet(u32())

            WASMOpcode.I32_CONST -> WASMInstruction.I32Const(readVarInt().toInt())
            WASMOpcode.I64_CONST -> WASMInstruction.I64Const(readVarInt())
            WASMOpcode.F32_CONST -> WASMInstruction.F32Const(f32())
            WASMOpcode.F64_CONST -> WASMInstruction.F64Const(f64())

            WASMOpcode.DROP -> WASMInstruction.Drop

            WASMOpcode.REF_NULL -> WASMInstruction.RefNull(readHeapTypeOnly())
            WASMOpcode.REF_IS_NULL -> WASMInstruction.RefIsNull
            WASMOpcode.REF_AS_NON_NULL -> WASMInstruction.RefAsNonNull

            // i32 comparisons
            WASMOpcode.I32_EQZ,
            WASMOpcode.I32_EQ,
            WASMOpcode.I32_NE,
            WASMOpcode.I32_LT_S,
            WASMOpcode.I32_LT_U,
            WASMOpcode.I32_GT_S,
            WASMOpcode.I32_GT_U,
            WASMOpcode.I32_LE_S,
            WASMOpcode.I32_LE_U,
            WASMOpcode.I32_GE_S,
            WASMOpcode.I32_GE_U,

                // i64 comparisons
            WASMOpcode.I64_EQZ,
            WASMOpcode.I64_EQ,
            WASMOpcode.I64_NE,
            WASMOpcode.I64_LT_S,
            WASMOpcode.I64_LT_U,
            WASMOpcode.I64_GT_S,
            WASMOpcode.I64_GT_U,
            WASMOpcode.I64_LE_S,
            WASMOpcode.I64_LE_U,
            WASMOpcode.I64_GE_S,
            WASMOpcode.I64_GE_U,

                // f32 comparisons
            WASMOpcode.F32_EQ,
            WASMOpcode.F32_NE,
            WASMOpcode.F32_LT,
            WASMOpcode.F32_GT,
            WASMOpcode.F32_LE,
            WASMOpcode.F32_GE,

                // f64 comparisons
            WASMOpcode.F64_EQ,
            WASMOpcode.F64_NE,
            WASMOpcode.F64_LT,
            WASMOpcode.F64_GT,
            WASMOpcode.F64_LE,
            WASMOpcode.F64_GE,

                // i32 math
            WASMOpcode.I32_ADD,
            WASMOpcode.I32_SUB,
            WASMOpcode.I32_MUL,
            WASMOpcode.I32_DIV_S,
            WASMOpcode.I32_DIV_U,
            WASMOpcode.I32_REM_S,
            WASMOpcode.I32_REM_U,

                // i64 math
            WASMOpcode.I64_ADD,
            WASMOpcode.I64_SUB,
            WASMOpcode.I64_MUL,
            WASMOpcode.I64_DIV_S,
            WASMOpcode.I64_DIV_U,
            WASMOpcode.I64_REM_S,
            WASMOpcode.I64_REM_U,

                // f32 math
            WASMOpcode.F32_ADD,
            WASMOpcode.F32_SUB,
            WASMOpcode.F32_MUL,
            WASMOpcode.F32_DIV,
            WASMOpcode.F32_MIN,
            WASMOpcode.F32_MAX,
            WASMOpcode.F32_REM,

                // f64 math
            WASMOpcode.F64_ADD,
            WASMOpcode.F64_SUB,
            WASMOpcode.F64_MUL,
            WASMOpcode.F64_DIV,
            WASMOpcode.F64_MIN,
            WASMOpcode.F64_MAX,
            WASMOpcode.F64_REM -> WASMInstruction.simple[opcode]

            0xfb -> {
                when (val gcOpcode = u32()) {

                    0x00 -> WASMInstruction.StructNew(u32())
                    0x01 -> WASMInstruction.StructNewDefault(u32())

                    0x02 -> {
                        val typeIndex = u32()
                        val fieldIndex = u32()
                        WASMInstruction.StructGet(typeIndex, fieldIndex)
                    }

                    0x05 -> {
                        val typeIndex = u32()
                        val fieldIndex = u32()
                        WASMInstruction.StructSet(typeIndex, fieldIndex)
                    }

                    0x07 -> WASMInstruction.ArrayNewDefault(u32())
                    0x0e -> WASMInstruction.ArraySet(u32())
                    0x0b -> WASMInstruction.ArrayGet(u32())

                    else -> error("Unknown GC opcode: $gcOpcode")
                }
            }

            else -> error("Unknown opcode: 0x${opcode.toString(16)}")
        }
    }
}