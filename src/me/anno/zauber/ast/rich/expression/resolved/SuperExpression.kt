package me.anno.zauber.ast.rich.expression.resolved

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution.resolveThisType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types

class SuperExpression(val label: Scope, val isThis: Boolean, scope: Scope, origin: Long) : Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String = "super@$label"
    override fun resolveReturnType(context: ResolutionContext): Type {
        return resolveThisType(context, label)
    }

    override fun resolveThrownType(context: ResolutionContext): Type = Types.Nothing
    override fun resolveYieldedType(context: ResolutionContext): Type = Types.Nothing

    override fun clone(scope: Scope) = SuperExpression(label, isThis, scope, origin)
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // should not have any
    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = true
    override fun forEachExpression(callback: (Expression) -> Unit) {}
}