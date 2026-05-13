package me.anno.generation.wasm

sealed class WASMType(val wasmName: String) {

    object I32 : WASMType("i32")
    object I64 : WASMType("i64")
    object F32 : WASMType("f32")
    object F64 : WASMType("f64")

    companion object {
        val anyRef = Ref(-0x10 /* magic value meaning any */, "any", true)
    }

    class Ref(
        val typeIndex: Int,
        typeName: String,
        val isNullable: Boolean
    ) : WASMType(
        if (isNullable) "(ref null $$typeName)"
        else "(ref $$typeName)"
    )

    val byteSize: Int
        get() = when (this) {
            I32, F32 -> 4
            I64, F64 -> 8
            is Ref -> 4
        }
}