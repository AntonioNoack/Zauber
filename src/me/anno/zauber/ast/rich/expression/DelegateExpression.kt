package me.anno.zauber.ast.rich.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

// todo this generates a hidden field, initializes it, and creates a setter and getter method
//  val x by lazy {} ->
//  val __x = lazy {}
//  fun getX() = __x.getValue()
//  fun setX(value) { __x.setValue(value) }

class DelegateExpression(val value: Expression) : Expression(value.scope, value.origin) {

    override fun toStringImpl(depth: Int): String {
        return "by ${value.toString(depth)}"
    }

    override fun resolveReturnType(context: ResolutionContext): Type = value.resolveReturnType(context)
    override fun resolveThrownType(context: ResolutionContext): Type = value.resolveThrownType(context)
    override fun resolveYieldedType(context: ResolutionContext): Type = value.resolveYieldedType(context)

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = value.hasLambdaOrUnknownGenericsType(context)
    override fun needsBackingField(methodScope: Scope): Boolean = value.needsBackingField(methodScope)
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = false

    override fun clone(scope: Scope) = DelegateExpression(value.clone(scope))

    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(value)
    }
}