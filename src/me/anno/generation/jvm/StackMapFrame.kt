package me.anno.generation.jvm

data class StackMapFrame(
    val offsetDelta: Int,
    val locals: List<VerificationTypeInfo>,
    val stack: List<VerificationTypeInfo>
)