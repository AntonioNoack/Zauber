package me.anno.zauber.ast.rich

import me.anno.zauber.ast.rich.controlflow.IfElseBranch
import me.anno.zauber.ast.rich.expression.AssignmentExpression
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.FieldExpression
import me.anno.zauber.ast.rich.expression.IsInstanceOfExpr
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type

object CastExpression {

    fun createCastExpression(
        expr: Expression, scope: Scope, origin: Int, type: Type,
        ifFalseExpr: (Scope) -> Expression
    ): Expression {
        return createBranchExpression(expr, scope, origin, { fieldExpr ->
            IsInstanceOfExpr(fieldExpr, type, scope, origin)
        }, { fieldExpr, ifTrueScope ->
            fieldExpr.clone(ifTrueScope)
        }, ifFalseExpr)
    }

    fun createBranchExpression(
        expr: Expression, scope: Scope, origin: Int,
        condition: (FieldExpression) -> Expression,
        ifTrueExpr: (FieldExpression, Scope) -> Expression,
        ifFalseExpr: (Scope) -> Expression,
    ): Expression {
        // we need to store the variable in a temporary field
        val tmpField = scope.createImmutableField(expr)
        val ifTrueScope = scope.getOrPut(scope.generateName("ifTrue"), ScopeType.METHOD_BODY)
        val ifFalseScope = scope.getOrPut(scope.generateName("ifFalse"), ScopeType.METHOD_BODY)
        val fieldExpr = FieldExpression(tmpField, scope, origin)
        val condition = condition(fieldExpr)
        val ifTrueExpr = ifTrueExpr(fieldExpr, ifTrueScope)
        val ifFalseExpr = ifFalseExpr(ifFalseScope)
        return ExpressionList(
            listOf(
                AssignmentExpression(fieldExpr, expr),
                IfElseBranch(condition, ifTrueExpr, ifFalseExpr)
            ), scope, origin
        )/*.apply {
            LOGGER.info("Created branch: $this")
        }*/
    }

}