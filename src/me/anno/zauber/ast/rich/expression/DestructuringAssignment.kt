package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.Field

fun createDestructuringAssignment(
    names: List<String>, initialValue: Expression,
    isMutable: Boolean
): Expression {
    val scope = initialValue.scope
    val origin = initialValue.origin
    val tmpField = scope.generateImmutableField(initialValue)
    val result = ArrayList<Expression>(1 + names.size)
    val tmpFieldExpr = FieldExpression(tmpField, scope, origin)
    result.add(AssignmentExpression(tmpFieldExpr, initialValue))
    for (i in names.indices) {
        val name = names[i]
        if (name == "_") continue
        val newValue = NamedCallExpression(
            tmpFieldExpr, "component${i + 1}",
            emptyList(), emptyList(), scope, origin
        )
        val newField = Field(
            scope, null, isMutable, null,
            name, null, newValue, emptyList(), origin
        )
        val newFieldExpr = FieldExpression(newField, scope, origin)
        result.add(AssignmentExpression(newFieldExpr, newValue))
    }
    return ExpressionList(result, scope, origin)
}