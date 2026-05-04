package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types

class WhileLoop(val condition: Expression, val body: Expression, val label: String?, val elseBranch: Expression?) :
    Expression(condition.scope, condition.origin) {

    override fun toStringImpl(depth: Int): String {
        val labelOrEmpty = if (label != null) "$label@" else ""
        var base = "${labelOrEmpty}while(${condition.toString(depth)}) { ${body.toString(depth)} }"
        if (elseBranch != null) {
            base += " else { $elseBranch }"
        }
        return base
    }

    override fun resolveReturnType(context: ResolutionContext): Type = exprHasNoType(context)
    override fun resolveThrownType(context: ResolutionContext): Type = Types.Nothing
    override fun resolveYieldedType(context: ResolutionContext): Type = Types.Nothing

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // this has no return value
    override fun needsBackingField(methodScope: Scope): Boolean = condition.needsBackingField(methodScope) ||
            body.needsBackingField(methodScope) ||
            (elseBranch?.needsBackingField(methodScope) ?: false)

    override fun clone(scope: Scope) =
        WhileLoop(condition.clone(scope), body.clone(body.scope), label, elseBranch?.clone(elseBranch.scope))

    // todo while-loop without break can enforce a condition, too
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean =
        condition.isResolved() && body.isResolved() && (elseBranch?.isResolved() ?: true)

    override fun resolveImpl(context: ResolutionContext) =
        WhileLoop(condition.resolve(context), body.resolve(context), label, elseBranch?.resolve(context))

    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(condition)
        callback(body)
        if (elseBranch != null) callback(elseBranch)
    }

}