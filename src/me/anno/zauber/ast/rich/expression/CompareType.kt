package me.anno.zauber.ast.rich.expression

enum class CompareType(
    val symbol: String,
    val isLess: Boolean,
    val isEquals: Boolean
) {
    LESS("<", true, false),
    GREATER(">", false, false),
    LESS_EQUALS("<=", true, true),
    GREATER_EQUALS(">=", false, true);

    fun eval(compareToResult: Int): Boolean {
        return when (this) {
            LESS -> compareToResult < 0
            LESS_EQUALS -> compareToResult <= 0
            GREATER -> compareToResult > 0
            GREATER_EQUALS -> compareToResult >= 0
        }
    }
}