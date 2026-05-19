package me.anno.generation.llvm

data class LLVMStruct(
    val superType: LLVMStruct?,
    val typeIndex: Int,
    val typeName: String,
    val properties: List<LLVMProperty>,
    val isNullable: Boolean,
) {
    override fun toString(): String {
        return if (superType != null) {
            "LLVMStruct('$typeName' extends '${superType.typeName}', $properties)"
        } else {
            "LLVMStruct('$typeName', $properties)"
        }
    }
}