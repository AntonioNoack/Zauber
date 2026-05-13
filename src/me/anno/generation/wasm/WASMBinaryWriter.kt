package me.anno.generation.wasm

import me.anno.utils.ByteArrayOutputStream2
import me.anno.utils.ListOfByteArrays
import me.anno.zauber.types.specialization.MethodSpecialization

class WASMBinaryWriter(val out: ByteArrayOutputStream2 = ByteArrayOutputStream2()) {

    fun u8(v: Int) = out.write(v)
    fun u32(v: Int) = writeVarUInt(v.toLong())

    fun f32(v: Float) {
        val encoded = v.toRawBits()
        le32(encoded)
    }

    fun f64(v: Double) {
        val encoded = v.toRawBits()
        le32(encoded.toInt())
        le32(encoded.shr(32).toInt())
    }

    fun le32(encoded: Int) {
        out.write(encoded)
        out.write(encoded shr 8)
        out.write(encoded shr 16)
        out.write(encoded shr 24)
    }

    // LEB128 (required for WASM)
    fun writeVarUInt(value: Long) {
        var v = value
        while (true) {
            var byte = (v and 0x7f).toInt()
            v = v ushr 7
            if (v != 0L) byte = byte or 0x80
            out.write(byte)
            if (v == 0L) break
        }
    }

    fun writeVarInt(value: Long) {
        var v = value
        while (true) {
            var byte = (v and 0x7f).toInt()
            v = v shr 7
            val done =
                (v == 0L && (byte and 0x40) == 0) ||
                        (v == -1L && (byte and 0x40) != 0)
            if (!done) byte = byte or 0x80
            out.write(byte)
            if (done) break
        }
    }

    fun i32Const(v: Int) {
        u8(WASMOpcode.I32_CONST)
        writeVarInt(v.toLong())
    }

    fun i64Const(v: Long) {
        u8(WASMOpcode.I64_CONST)
        writeVarInt(v)
    }

    fun f32Const(v: Float) {
        u8(WASMOpcode.F32_CONST)
        f32(v)
    }

    fun f64Const(v: Double) {
        u8(WASMOpcode.F64_CONST)
        f64(v)
    }

    fun localGet(i: Int) {
        u8(WASMOpcode.LOCAL_GET)
        u32(i)
    }

    fun localSet(i: Int) {
        u8(WASMOpcode.LOCAL_SET)
        u32(i)
    }

    fun globalGet(i: Int) {
        u8(WASMOpcode.GLOBAL_GET)
        u32(i)
    }

    fun globalSet(i: Int) {
        u8(WASMOpcode.GLOBAL_SET)
        u32(i)
    }

    fun call(i: Int) {
        u8(WASMOpcode.CALL)
        u32(i)
    }

    fun drop() {
        u8(WASMOpcode.DROP)
    }

    fun ret() {
        u8(WASMOpcode.RETURN)
    }

    fun unreachable() {
        u8(WASMOpcode.UNREACHABLE)
    }

    fun structNew(typeIndex: Int) {
        u8(0xfb)
        u32(0x00)
        u32(typeIndex)
    }

    fun structGet(typeIndex: Int, fieldIndex: Int) {
        u8(0xfb)
        u32(0x02)
        u32(typeIndex)
        u32(fieldIndex)
    }

    fun structSet(typeIndex: Int, fieldIndex: Int) {
        u8(0xfb)
        u32(0x05)
        u32(typeIndex)
        u32(fieldIndex)
    }

    fun writeModuleHeader() {
        le32(0x6D736100) // \0asm
        le32(1) // version 1
    }

    fun writeFunctionSection(
        functions: List<MethodSpecialization>,
        getFunctionType: (MethodSpecialization) -> Int,
    ) {
        u8(0x03) // Function section id
        val t = WASMBinaryWriter()
        t.u32(functions.size)
        for (fn in functions) {
            t.u32(getFunctionType(fn))
        }
        endSection(t)
    }

    fun writeTypeSection(functionTypeList: List<FunctionType>) {
        u8(0x01)

        val t = WASMBinaryWriter()
        t.u32(functionTypeList.size)

        for (ft in functionTypeList) {
            t.u8(0x60) // func type

            t.u32(ft.params.size)
            for (p in ft.params) {
                t.writeValueType(p)
            }

            t.u32(ft.results.size)
            for (r in ft.results) {
                t.writeValueType(r)
            }
        }
        endSection(t)
    }

    fun writeMemorySection() {
        u8(0x05) // memory section

        val t = WASMBinaryWriter()
        t.u32(1) // one memory

        t.u8(0x00) // limits flag
        t.u32(64)  // initial pages

        endSection(t)
    }

    fun endSection(t: WASMBinaryWriter) {
        val bytes = t.out
        u32(bytes.size)
        out.write(bytes.bytes, 0, bytes.size)
    }

    fun writeImportSection(imports: List<Pair<String, Int>>) {
        u8(0x02) // import section

        val t = WASMBinaryWriter()
        t.u32(imports.size)

        for ((name, typeIndex) in imports) {

            // module name
            t.writeNameAsBytes("env")

            // import name
            t.writeNameAsBytes(name)

            // import kind = function
            t.u8(0x00)

            // type index
            // assumes functionTypes/functionTypeList already contains this type
            t.u32(typeIndex)
        }
        endSection(t)
    }

    fun writeExportSection(exports: List<Pair<String, Int>>) {
        u8(0x07)

        val t = WASMBinaryWriter()
        t.u32(exports.size)
        for ((name, idx) in exports) {
            t.writeNameAsBytes(name)
            t.u8(0x00) // function export
            t.u32(idx)
        }
        endSection(t)
    }

    private fun writeNameAsBytes(name: String) {
        u32(name.length)
        for (i in name.indices) {
            // pretty unsafe, but we shouldn't have special chars anyway
            out.write(name[i].code)
        }
    }

    fun writeCodeSection(bodies: ListOfByteArrays) {
        u8(0x0A) // code section id

        val tmp = WASMBinaryWriter()
        tmp.u32(bodies.size)
        for (bi in 0 until bodies.size) {
            val i0 = bodies.getI0(bi)
            val i1 = bodies.getI1(bi)
            val total = i1 - i0

            tmp.u32(total)
            tmp.out.write(bodies.bytes.bytes, i0, i1)
        }
        endSection(tmp)
    }


    fun writeValueType(type: WASMType) {
        when (type) {
            WASMType.I32 -> u8(0x7f)
            WASMType.I64 -> u8(0x7e)
            WASMType.F32 -> u8(0x7d)
            WASMType.F64 -> u8(0x7c)
            is WASMType.Ref -> writeHeapType(type)
        }
    }

    fun writeHeapType(type: WASMType.Ref) {
        u8(if (type.isNullable) 0x63 else 0x64)
        writeVarInt(type.typeIndex.toLong())
    }

}