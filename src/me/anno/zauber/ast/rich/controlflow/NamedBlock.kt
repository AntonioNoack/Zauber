package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.types.Scope

fun createNamedBlock(body: Expression, label: String?, scope: Scope, origin: Int):
        DoWhileLoop {
    val falseExpr = SpecialValueExpression(SpecialValue.FALSE, scope, origin)
    return DoWhileLoop(body = body, condition = falseExpr, label)
}