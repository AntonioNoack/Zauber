package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.types.Scope

@Suppress("FunctionName")
fun DeclarationExpression(
    scope: Scope, initialValue: Expression?,
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