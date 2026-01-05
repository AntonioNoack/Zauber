package me.anno.zauber.ast.simple

import me.anno.zauber.ast.rich.expression.Expression

class SimpleBlock {

    fun add(expr: SimpleExpression) {
        instructions.add(expr)
    }

    fun field(expr: Expression): SimpleField {
        return SimpleField()
    }

    val declaredFields = ArrayList<SimpleField>()
    val instructions = ArrayList<SimpleExpression>()
}