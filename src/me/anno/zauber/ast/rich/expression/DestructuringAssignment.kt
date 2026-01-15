package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.ASTBuilderBase
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.FieldDeclaration
import me.anno.zauber.ast.rich.Keywords
import me.anno.zauber.ast.rich.expression.unresolved.AssignmentExpression
import me.anno.zauber.ast.rich.expression.unresolved.FieldExpression
import me.anno.zauber.ast.rich.expression.unresolved.NamedCallExpression
import me.anno.zauber.types.Scope

fun ASTBuilderBase.createDestructuringAssignment(
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
        val name1 = "component${i + 1}"
        val newValue = NamedCallExpression(
            tmpFieldExpr,
            name1, nameAsImport(name1),
            scope, origin
        )
        val newField = Field(
            fieldScope, null,
            false, isMutable = isMutable, null,
            name.name, name.type, newValue, Keywords.NONE, origin
        )
        val newFieldExpr = FieldExpression(newField, scope, origin)
        result.add(AssignmentExpression(newFieldExpr, newValue))
    }
    return ExpressionList(result, scope, origin)
}