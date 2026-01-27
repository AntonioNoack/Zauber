package me.anno.zauber.ast.rich.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class TypeExpression(val type: Type, scope: Scope, origin: Int) :
    Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String = type.toString()
    override fun clone(scope: Scope) = TypeExpression(type, scope, origin)
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun resolveType(context: ResolutionContext): Type = type
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = type.isResolved()
    override fun forEachExpression(callback: (Expression) -> Unit) {}
}