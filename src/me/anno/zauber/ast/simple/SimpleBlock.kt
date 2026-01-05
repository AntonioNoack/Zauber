package me.anno.zauber.ast.simple

import me.anno.zauber.ast.rich.expression.Expression

class SimpleBlock(val graph: SimpleGraph) {

    fun add(expr: SimpleExpression) {
        instructions.add(expr)
    }

    fun field(expr: Expression): SimpleField {
        val field = SimpleField(graph.numFields++)
        declaredFields.add(field)
        return field
    }

    val declaredFields = ArrayList<SimpleField>()
    val instructions = ArrayList<SimpleExpression>()

    override fun toString(): String {
        return instructions.joinToString("\n")
    }
}