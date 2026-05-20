package me.anno.generation.wasm

class WASMArray(
    superType: WASMStructLike?,
    typeIndex: Int, typeName: String,
    val elementStruct: WASMStruct?,
    val elementType: WASMType,
    isNullable: Boolean,
) : WASMStructLike(
    superType, typeIndex, typeName, isNullable,
    WASMType.Array(typeIndex, typeName, isNullable)
) {
    override fun toString(): String {
        return "WASMArray($type)"
    }
}