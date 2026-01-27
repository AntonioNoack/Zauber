package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class WhileLoop(val condition: Expression, val body: Expression, val label: String?) :
    Expression(condition.scope, condition.origin) {

    override fun toStringImpl(depth: Int): String {
        return "${if (label != null) "$label@" else ""} while(${condition.toString(depth)}) { ${body.toString(depth)} }"
    }

    override fun resolveType(context: ResolutionContext): Type = exprHasNoType(context)
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // this has no return value
    override fun needsBackingField(methodScope: Scope): Boolean = condition.needsBackingField(methodScope) ||
            body.needsBackingField(methodScope)

    override fun clone(scope: Scope) = WhileLoop(condition.clone(scope), body.clone(body.scope), label)

    // todo while-loop without break can enforce a condition, too
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = condition.isResolved() && body.isResolved()
    override fun resolveImpl(context: ResolutionContext) =
        WhileLoop(condition.resolve(context), body.resolve(context), label)

    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(condition)
        callback(body)
    }

}