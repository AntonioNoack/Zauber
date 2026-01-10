package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.FieldDeclaration
import me.anno.zauber.ast.rich.Keywords
import me.anno.zauber.ast.rich.expression.*
import me.anno.zauber.types.Scope

fun destructuringForLoop(
    scope: Scope,
    variableNames: List<FieldDeclaration>, iterable: Expression,
    body: Expression, label: String?
): Expression {
    val origin = iterable.origin
    val fullName = scope.generateName("destruct")
    val fullVariable = Field(
        scope, null,
        false, null,
        fullName, null, null,
        Keywords.NONE, origin
    )
    val fields = variableNames.map { fieldDeclaration ->
        // todo if _, don't create a field
        if (fieldDeclaration.name != "_") Field(
            scope, null,
            false, null, fieldDeclaration.name,
            fieldDeclaration.type, null,
            Keywords.NONE, origin
        ) else null
    }
    val fullExpr = FieldExpression(fullVariable, scope, origin)
    val newBody = ExpressionList(
        variableNames
            .withIndex()
            .filter { it.value.name != "_" }
            .map { (index, _) ->
                val newValue = NamedCallExpression(
                    fullExpr, "component${index + 1}", emptyList(),
                    emptyList(), scope, origin
                )
                val variableName = FieldExpression(fields[index]!!, scope, origin)
                AssignmentExpression(variableName, newValue)
            } + body, scope, origin
    )
    return forLoop(fullVariable, iterable, newBody, label)
}