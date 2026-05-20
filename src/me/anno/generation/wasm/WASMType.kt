package me.anno.generation.wasm

sealed class WASMType(val wasmName: String) {

    companion object {
        val anyRef = Ref(-0x10 /* magic value meaning any */, "any", true)

        fun Array(
            typeIndex: Int,
            typeName: String,
            isNullable: Boolean
        ) = Ref(
            typeIndex, typeName, isNullable,
            typeName
        )
    }

    sealed class WASMNumberType(wasmName: String) : WASMType(wasmName)

    // todo these can be used for arrays
    object I8 : WASMNumberType("i8")
    object I16 : WASMNumberType("i16")

    object I32 : WASMNumberType("i32")
    object I64 : WASMNumberType("i64")
    object F32 : WASMNumberType("f32")
    object F64 : WASMNumberType("f64")

    override fun toString(): String = wasmName

    class Ref(
        val typeIndex: Int,
        typeName: String,
        val isNullable: Boolean,
        wasmName: String = "(ref null $typeName)"
    ) : WASMType(wasmName)

}