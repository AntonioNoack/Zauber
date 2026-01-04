package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.PrefixExpression
import me.anno.zauber.ast.rich.expression.PrefixType
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression

@Suppress("FunctionName")
fun DoWhileLoop(body: Expression, condition: Expression, label: String?): Expression {
    val origin = body.origin
    val negatedCondition = PrefixExpression(PrefixType.NOT, origin, condition)
    val breakI = BreakExpression(label, body.scope, origin)
    val newBody = ExpressionList(
        listOf(
            condition,
            IfElseBranch(negatedCondition, breakI, null)
        ), body.scope, origin
    )
    return WhileLoop(SpecialValueExpression(SpecialValue.TRUE, body.scope, origin), newBody, label)
}