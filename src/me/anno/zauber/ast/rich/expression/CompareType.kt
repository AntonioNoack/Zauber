package me.anno.zauber.ast.rich.expression

enum class CompareType(val symbol: String) {
    LESS("<"),
    GREATER(">"),
    LESS_EQUALS("<="),
    GREATER_EQUALS(">="),
}