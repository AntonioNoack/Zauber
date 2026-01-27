package me.anno.zauber.ast.rich.expression.resolved

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution.resolveThisType
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

// todo label may depend on aliases (imports) -> resolve label to a scope
class ThisExpression(val label: Scope, scope: Scope, origin: Int) : Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String = "this@$label"
    override fun resolveType(context: ResolutionContext): Type {
        return resolveThisType(label)
    }

    override fun clone(scope: Scope) = ThisExpression(label, scope, origin)
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // should not have any
    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = true
    override fun forEachExpression(callback: (Expression) -> Unit) {}
}