package me.anno.zauber.interpreting

enum class ReturnType(val symbol: String) {
    RETURN("return"),
    THROW("throw"),
    YIELD("yield"),

    VALUE("")
}