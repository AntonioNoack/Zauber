package me.anno.zauber.astbuilder.controlflow

import me.anno.zauber.astbuilder.expression.Expression
import me.anno.zauber.astbuilder.expression.ExpressionList
import me.anno.zauber.astbuilder.expression.PrefixExpression
import me.anno.zauber.astbuilder.expression.PrefixType
import me.anno.zauber.astbuilder.expression.constants.SpecialValue
import me.anno.zauber.astbuilder.expression.constants.SpecialValueExpression

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