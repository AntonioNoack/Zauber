package me.anno.support.jvm.expression

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

class LazyJVMExpression(
    val getValue: () -> Expression,
    scope: Scope, origin: Long,
) : Expression(scope, origin) {

    val value by lazy { getValue() }

    override fun resolveValueType(context: ResolutionContext): Type = value.resolveValueType(context)
    override fun clone(scope: Scope): Expression = value.clone(scope)
    override fun toStringImpl(depth: Int): String = "LazyJVMExpression($value)"

    override fun resolveImpl(context: ResolutionContext): Expression = value.resolveImpl(context)

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean =
        value.hasLambdaOrUnknownGenericsType(context)

    override fun needsBackingField(methodScope: Scope): Boolean = value.needsBackingField(methodScope)
    override fun splitsScope(): Boolean = value.splitsScope()
    override fun isResolved(): Boolean = false

    override fun forEachExpression(callback: (Expression) -> Unit) {
        value.forEachExpression(callback)
    }

}