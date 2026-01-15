package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.expression.unresolved.AssignmentExpression
import me.anno.zauber.ast.rich.expression.unresolved.FieldExpression
import me.anno.zauber.types.Scope

fun createDeclarationExpression(
    scope: Scope,
    initialValue: Expression?,
    field: Field
): Expression {
    val origin = field.origin
    return if (initialValue != null) {
        val variableName = FieldExpression(field, scope, origin)
        AssignmentExpression(variableName, initialValue)
    } else {
        ExpressionList(emptyList(), scope, origin)
    }
}