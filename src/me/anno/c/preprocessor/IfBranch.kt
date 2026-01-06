package me.anno.c.preprocessor

data class IfBranch(
    val shouldEmit: () -> Boolean,
    val start: Int,
    val end: Int
)
