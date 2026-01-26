package me.anno.zauber.ast.rich.expression

enum class CompareType(val symbol: String) {
    LESS("<"),
    GREATER(">"),
    LESS_EQUALS("<="),
    GREATER_EQUALS(">=");

    fun eval(compareToResult: Int): Boolean {
        return when (this) {
            LESS -> compareToResult < 0
            LESS_EQUALS -> compareToResult <= 0
            GREATER -> compareToResult > 0
            GREATER_EQUALS -> compareToResult >= 0
        }
    }
}