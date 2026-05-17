package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.parser.ASTBuilderBase
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.createDeclarationExpression
import me.anno.zauber.ast.rich.expression.unresolved.FieldExpression
import me.anno.zauber.ast.rich.expression.unresolved.NamedCallExpression
import me.anno.zauber.scope.Scope

fun ASTBuilderBase.iterableToIterator(iterable: Expression): NamedCallExpression {
    return NamedCallExpression(
        iterable, "iterator", nameAsImport("iterator"),
        iterable.scope, iterable.origin
    )
}

fun ASTBuilderBase.iteratorToNext(iteratorFieldExpr: FieldExpression, scope: Scope, origin: Long): Expression {
    return NamedCallExpression(
        iteratorFieldExpr,
        "next", nameAsImport("next"),
        scope, origin
    )
}

/** just for deducting the type...
todo is there a better solution, that doesn't complicate our type system? */
fun ASTBuilderBase.iterableToNextExpr(iterable: Expression): NamedCallExpression {
    val iterator = iterableToIterator(iterable)
    return NamedCallExpression(
        iterator, "next", nameAsImport("next"),
        iterator.scope, iterator.origin
    )
}

fun ASTBuilderBase.iteratorToHasNext(iteratorFieldExpr: FieldExpression, scope: Scope, origin: Long): NamedCallExpression {
    return NamedCallExpression(
        iteratorFieldExpr,
        "hasNext", nameAsImport("hasNext"),
        scope, origin
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
    body: Expression, label: String?,
    elseBranch: Expression? = null
): Expression {
    val scope = iterable.scope
    val origin = iterable.origin
    val iterator = iterableToIterator(iterable)
    val iteratorField = scope.createImmutableField(iterator)
    val iteratorFieldExpr = FieldExpression(iteratorField, scope, origin)
    val getNextCall = iteratorToNext(iteratorFieldExpr, body.scope, body.origin)
    val outerAssignment = createDeclarationExpression(scope, iterator, iteratorField)
    val innerAssignment = createDeclarationExpression(body.scope, getNextCall, variableField)
    val hasNextCall = iteratorToHasNext(iteratorFieldExpr, scope, origin)
    val newBody = ExpressionList(listOf(innerAssignment, body), body.scope, body.origin)
    val loop = WhileLoop(hasNextCall, newBody, label, elseBranch)
    return ExpressionList(listOf(outerAssignment, loop), scope, origin)
}