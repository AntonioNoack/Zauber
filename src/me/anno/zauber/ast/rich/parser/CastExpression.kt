package me.anno.zauber.ast.rich.parser

import me.anno.zauber.ast.rich.controlflow.IfElseBranch
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.IsInstanceOfExpr
import me.anno.zauber.ast.rich.expression.unresolved.AssignmentExpression
import me.anno.zauber.ast.rich.expression.unresolved.FieldExpression
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.types.Type

fun createCastExpression(
    expr: Expression, scope: Scope, origin: Long, type: Type,
    ifFalseExpr: (Scope) -> Expression
): Expression {
    return createBranchExpression(expr, scope, origin, { fieldExpr ->
        IsInstanceOfExpr(fieldExpr, type, scope, origin)
    }, { fieldExpr, ifTrueScope ->
        fieldExpr.clone(ifTrueScope)
    }, ifFalseExpr)
}

fun createBranchExpression(
    expr: Expression, scope: Scope, origin: Long,
    condition: (FieldExpression) -> Expression,
    ifTrueExpr: (FieldExpression, Scope) -> Expression,
    ifFalseExpr: (Scope) -> Expression,
): Expression {
    // we need to store the variable in a temporary field
    val tmpField = scope.createImmutableField(expr, "branch", origin)
    val ifTrueScope = scope.generate("ifTrue", origin, ScopeType.METHOD_BODY)
    val ifFalseScope = scope.generate("ifFalse", origin, ScopeType.METHOD_BODY)
    val tmpFieldExpr = FieldExpression(tmpField, scope, origin)
    val condition = condition(tmpFieldExpr)
    val ifTrueExpr = ifTrueExpr(FieldExpression(tmpField, ifTrueScope, origin), ifTrueScope)
    val ifFalseExpr = ifFalseExpr(ifFalseScope)
    return ExpressionList(
        scope, origin,
        AssignmentExpression(tmpFieldExpr, expr),
        IfElseBranch(condition, ifTrueExpr, ifFalseExpr)
    )
}
