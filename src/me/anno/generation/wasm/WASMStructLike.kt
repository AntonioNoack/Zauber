package me.anno.generation.wasm

abstract class WASMStructLike(
    val superType: WASMStructLike?,
    val typeIndex: Int,
    val typeName: String,
    val isNullable: Boolean,
    val type: WASMType
) : WASMFuncTypeOrStruct()