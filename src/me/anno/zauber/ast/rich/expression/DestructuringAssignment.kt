package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.FieldDeclaration
import me.anno.zauber.ast.rich.Keywords
import me.anno.zauber.types.Scope

fun createDestructuringAssignment(
    names: List<FieldDeclaration>, initialValue: Expression,
    isMutable: Boolean, fieldScope: Scope
): Expression {
    val scope = initialValue.scope
    val origin = initialValue.origin
    val tmpField = scope.createImmutableField(initialValue)
    val result = ArrayList<Expression>(1 + names.size)
    val tmpFieldExpr = FieldExpression(tmpField, scope, origin)
    result.add(AssignmentExpression(tmpFieldExpr, initialValue))
    for (i in names.indices) {
        val name = names[i]
        if (name.name == "_") continue
        val newValue = NamedCallExpression(
            tmpFieldExpr, "component${i + 1}",
            emptyList(), emptyList(), scope, origin
        )
        val newField = Field(
            fieldScope, null, isMutable, null,
            name.name, name.type, newValue, Keywords.NONE, origin
        )
        val newFieldExpr = FieldExpression(newField, scope, origin)
        result.add(AssignmentExpression(newFieldExpr, newValue))
    }
    return ExpressionList(result, scope, origin)
}