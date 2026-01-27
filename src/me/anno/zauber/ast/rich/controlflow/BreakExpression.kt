package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class BreakExpression(val label: Scope, scope: Scope, origin: Int) : Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return "break@$label"
    }

    override fun resolveType(context: ResolutionContext): Type {
        return exprHasNoType(context)
    }

    override fun clone(scope: Scope): Expression = BreakExpression(label, scope, origin)

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // has no return type
    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = true
    override fun forEachExpression(callback: (Expression) -> Unit) {}
}