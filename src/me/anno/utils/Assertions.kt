package me.anno.utils

fun assertEquals(a: Int, b: Int, message: String) {
    if (a != b) throw AssertionError("$a != $b: $message")
}

inline fun assertEquals(a: Int, b: Int, message: () -> String = { "" }) {
    if (a != b) throw AssertionError("$a != $b: ${message()}")
}