package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.ASTBuilderBase
import me.anno.zauber.ast.rich.FieldDeclaration
import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.ast.rich.expression.unresolved.AssignmentExpression
import me.anno.zauber.ast.rich.expression.unresolved.FieldExpression
import me.anno.zauber.ast.rich.expression.unresolved.NamedCallExpression
import me.anno.zauber.scope.Scope

val componentNames = List(100) {
    "component${it + 1}"
}

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
        val name1 = componentNames[i]
        val newValue = NamedCallExpression(
            tmpFieldExpr,
            name1, nameAsImport(name1),
            scope, origin
        )
        val newField = fieldScope.addField(
            null, false, isMutable = isMutable, null,
            name.name, name.type, newValue, Flags.NONE, origin
        )
        val newFieldExpr = FieldExpression(newField, scope, origin)
        result.add(AssignmentExpression(newFieldExpr, newValue))
    }
    return ExpressionList(result, scope, origin)
}