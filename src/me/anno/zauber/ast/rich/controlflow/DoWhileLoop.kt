package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

/**
 * cannot easily be converted to a while-loop, because continue needs to run the evaluation!
 * */
class DoWhileLoop(val body: Expression, val condition: Expression, val label: String?) :
    Expression(condition.scope, condition.origin) {

    override fun toStringImpl(depth: Int): String {
        return "${if (label != null) "$label@" else ""} do { ${body.toString(depth)} } while (${condition.toString(depth)})"
    }

    override fun resolveType(context: ResolutionContext): Type = exprHasNoType(context)
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // this has no return value
    override fun needsBackingField(methodScope: Scope): Boolean = condition.needsBackingField(methodScope) ||
            body.needsBackingField(methodScope)

    override fun clone(scope: Scope) =
        DoWhileLoop(body = body.clone(body.scope), condition = condition.clone(scope), label)

    // todo while-loop without break can enforce a condition, too
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = condition.isResolved() && body.isResolved()
    override fun resolveImpl(context: ResolutionContext) =
        DoWhileLoop(body = body.resolve(context), condition = condition.resolve(context), label)

}