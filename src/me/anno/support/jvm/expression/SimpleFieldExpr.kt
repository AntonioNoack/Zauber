package me.anno.support.jvm.expression

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

class SimpleFieldExpr(val type: Type, val id: Int, scope: Scope, origin: Long) : Expression(scope, origin) {

    override fun resolveReturnType(context: ResolutionContext): Type = type
    override fun clone(scope: Scope): Expression = this
    override fun toStringImpl(depth: Int): String = "%$id"
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = true

    fun use() = this

    override fun forEachExpression(callback: (Expression) -> Unit) {
        // no contents
    }
}