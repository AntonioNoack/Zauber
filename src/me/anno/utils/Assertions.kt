package me.anno.utils

fun assertEquals(a: Int, b: Int, message: String) {
    if (a != b) throw AssertionError("$a != $b: $message")
}

inline fun assertEquals(a: Int, b: Int, message: () -> String = { "" }) {
    if (a != b) throw AssertionError("$a != $b: ${message()}")
}

fun assertEquals(a: Any?, b: Any?, message: String) {
    if (a != b) throw AssertionError("${a.format()} != ${b.format()}: $message")
}

inline fun assertEquals(a: Any?, b: Any?, message: () -> String = { "" }) {
    if (a != b) throw AssertionError("${a.format()} != ${b.format()}: ${message()}")
}

fun Any?.format() = toString()
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t")