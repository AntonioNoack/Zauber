package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.expression.unresolved.NamedCallExpression
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

/**
 * this generates a hidden field, initializes it, and creates a setter and getter method
 *   val x by lazy {} ->
 *   val __x = lazy {}
 *   fun getX() = __x.getValue()
 *   fun setX(value) { __x.setValue(value) }
 * */
class DelegateExpression(val value: Expression) : Expression(value.scope, value.origin) {

    override fun toStringImpl(depth: Int): String {
        return "by ${value.toString(depth)}"
    }

    override fun resolveReturnType(context: ResolutionContext): Type {
        val resolvedValue = value.resolve(context)
        // val baseType = resolvedValue.resolveReturnType(context).specialize(context)
        val tmpExpr = NamedCallExpression(resolvedValue, "getValue", emptyList(), scope, origin)
        return tmpExpr.resolveReturnType(context)
    }

    override fun resolveThrownType(context: ResolutionContext): Type = value.resolveThrownType(context)
    override fun resolveYieldedType(context: ResolutionContext): Type = value.resolveYieldedType(context)

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean =
        value.hasLambdaOrUnknownGenericsType(context)

    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = value.isResolved()

    override fun resolveImpl(context: ResolutionContext): Expression {
        return DelegateExpression(value.resolve(context))
    }

    override fun clone(scope: Scope) = DelegateExpression(value.clone(scope))

    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(value)
    }
}