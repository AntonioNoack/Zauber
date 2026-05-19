package me.anno.generation.llvm

sealed class LLVMType(val wasmName: String) {
    object I1 : LLVMType("i1")
    object I8 : LLVMType("i8")
    object I16 : LLVMType("i16")
    object I32 : LLVMType("i32")
    object I64 : LLVMType("i64")
    object F32 : LLVMType("float")
    object F64 : LLVMType("double")

    class Ptr(
        val element: LLVMType,
        val isValueType: Boolean
    ) : LLVMType("${element.wasmName}*")

    class Struct(val name: String) : LLVMType("%$name")

    val ir get() = wasmName
}