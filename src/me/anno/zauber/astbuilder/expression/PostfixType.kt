package me.anno.zauber.astbuilder.expression

enum class PostfixType(val symbol: String) {
    INCREMENT("++"),
    DECREMENT("--"),
    ENSURE_NOT_NULL("!!")
}
