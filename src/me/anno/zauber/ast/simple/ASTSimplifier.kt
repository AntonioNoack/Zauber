package me.anno.zauber.ast.simple

import me.anno.zauber.ast.rich.expression.Expression

object ASTSimplifier {
    fun simplify(expr: Expression): SimpleExpression {
        when (expr) {
            else -> TODO("Simplify ${expr.javaClass.simpleName}")
        }
    }
}