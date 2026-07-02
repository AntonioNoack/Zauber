package me.anno.generation.jvm

data class PendingFrame(
    val label: String,
    val locals: List<VerificationTypeInfo>,
    val stack: List<VerificationTypeInfo>
)