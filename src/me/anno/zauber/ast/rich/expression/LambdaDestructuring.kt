package me.anno.zauber.ast.rich.expression

class LambdaDestructuring(val names: List<String>) : LambdaVariable(null, "") {
    override fun toString(): String {
        return "(${names.joinToString(", ")})"
    }
}