package me.anno.generation.llvm

sealed class LLVMType(val ir: String) {
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
    ) : LLVMType("${element.ir}*")

    class Struct(val name: String, val isValueType: Boolean) : LLVMType("%$name")

}