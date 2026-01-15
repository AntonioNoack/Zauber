package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType

enum class ShortcutOperator {
    AND,
    OR
}

fun shortcutExpression(
    left: Expression, operator: ShortcutOperator, right: Expression,
    scope: Scope, origin: Int
): Expression {
    // todo we should have created the new scope immediately
    val bodyScope = scope.generate("shortcut", ScopeType.EXPRESSION)
    val rightWithScope = right.clone(bodyScope)
    return shortcutExpressionI(left, operator, rightWithScope, scope, origin)
}

fun shortcutExpressionI(
    left: Expression, operator: ShortcutOperator, rightWithScope: Expression,
    scope: Scope, origin: Int
): Expression {
    return when (operator) {
        ShortcutOperator.AND -> {
            val falseExpression = SpecialValueExpression(SpecialValue.FALSE, scope, origin)
            IfElseBranch(left, rightWithScope, falseExpression)
        }
        ShortcutOperator.OR -> {
            val trueExpression = SpecialValueExpression(SpecialValue.TRUE, scope, origin)
            IfElseBranch(left, trueExpression, rightWithScope)
        }
    }
}