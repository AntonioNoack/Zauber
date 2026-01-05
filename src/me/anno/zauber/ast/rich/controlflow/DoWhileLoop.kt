package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.types.BooleanUtils.not

fun createWhileLoop(body: Expression, condition: Expression, label: String?): WhileLoop {
    val origin = body.origin
    val negatedCondition = condition.not()
    val breakI = BreakExpression(label, body.scope, origin)
    val newBody = ExpressionList(
        listOf(
            condition,
            IfElseBranch(negatedCondition, breakI, null)
        ), body.scope, origin
    )
    return WhileLoop(TRUEExpr, newBody, label)
}