package me.anno.zauber.astbuilder.expression

import me.anno.zauber.astbuilder.Field

fun createDestructuringAssignment(
    names: List<String>, initialValue: Expression,
    isVar: Boolean, isLateinit: Boolean
): Expression {
    check(!isLateinit)
    val scope = initialValue.scope
    val origin = initialValue.origin
    val tmpField = scope.generateField(initialValue)
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
            scope, isVar, !isVar, null, name,
            null, newValue, emptyList(), origin
        )
        val newFieldExpr = FieldExpression(newField, scope, origin)
        result.add(AssignmentExpression(newFieldExpr, newValue))
    }
    return ExpressionList(result, scope, origin)
}