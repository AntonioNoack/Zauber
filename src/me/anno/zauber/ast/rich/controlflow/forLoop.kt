package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.ASTBuilderBase
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.expression.*

fun ASTBuilderBase.iterableToIterator(iterable: Expression): NamedCallExpression {
    return NamedCallExpression(
        iterable, "iterator", nameAsImport("iterator"),
         iterable.scope, iterable.origin
    )
}

fun ASTBuilderBase.iteratorToNext(iteratorField: Field, body: Expression): Expression {
    return NamedCallExpression(
        FieldExpression(iteratorField, body.scope, body.origin),
        "next", nameAsImport("next"),
        body.scope, body.origin
    )
}

/** just for deducting the type...
todo is there a better solution, that doesn't complicate our type system? */
fun ASTBuilderBase.iterableToNextExpr(iterable: Expression): Expression {
    val iterator = iterableToIterator(iterable)
    return NamedCallExpression(
        iterator, "next", nameAsImport("next"),
        iterator.scope, iterator.origin
    )
}

// for-loop with else like Python? :) idk if we want that complexity...
//  we would need break-with-expression
/**
 * val tmp = iterable()
 * while(tmp.hasNext()) {
 *    val variableName: variableType = tmp.next()
 *    body()
 * }
 * */
fun ASTBuilderBase.forLoop(
    variableField: Field, iterable: Expression,
    body: Expression, label: String?
): Expression {
    val scope = iterable.scope
    val origin = iterable.origin
    val iterator = iterableToIterator(iterable)
    val iteratorField = scope.createImmutableField(iterator)
    val getNextCall = iteratorToNext(iteratorField, body)
    val outerAssignment = DeclarationExpression(scope, iterator, iteratorField)
    val innerAssignment = DeclarationExpression(body.scope, getNextCall, variableField)
    val hasNextCall = NamedCallExpression(
        FieldExpression(iteratorField, scope, origin),
        "hasNext", nameAsImport("hasNext"),
        scope, origin
    )
    val newBody = ExpressionList(listOf(innerAssignment, body), body.scope, body.origin)
    val loop = WhileLoop(hasNextCall, newBody, label)
    return ExpressionList(listOf(outerAssignment, loop), scope, origin)
}