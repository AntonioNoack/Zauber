package me.anno.zauber.ast.rich.expression.unresolved

class LambdaDestructuring(val names: List<LambdaVariable>) : LambdaVariable(null, "") {
    override fun toString(): String {
        return "(${names.joinToString(", ")})"
    }
}