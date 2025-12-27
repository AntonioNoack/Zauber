package me.anno.zauber.astbuilder.expression

import me.anno.zauber.astbuilder.Field
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