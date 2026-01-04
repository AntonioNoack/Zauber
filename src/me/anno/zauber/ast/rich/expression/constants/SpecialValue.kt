package me.anno.zauber.ast.rich.expression.constants

enum class SpecialValue(val symbol: String) {
    TRUE("true"),
    FALSE("false"),
    NULL("null"),
    THIS("this"),
    SUPER("super")
}
