package me.anno.zauber.ast.simple

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.types.Scope

class SimpleBlock(val graph: SimpleGraph, scope: Scope, origin: Int) :
    SimpleExpression(scope, origin) {

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