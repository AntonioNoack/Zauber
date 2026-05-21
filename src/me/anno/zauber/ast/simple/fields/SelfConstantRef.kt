package me.anno.zauber.ast.simple.fields

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

/**
 * this is to be used for SimpleFields as ConstantRef
 * */
class SelfConstantRef(val type: Type, val kind: ConstantKind, scope: Scope, origin: Long) : Expression(scope, origin) {

    override fun resolveReturnType(context: ResolutionContext): Type = type.specialize(context)
    override fun clone(scope: Scope): Expression = this
    override fun toStringImpl(depth: Int): String = kind.toString()
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = true

    override fun forEachExpression(callback: (Expression) -> Unit) {
    }

    enum class ConstantKind {
        EXPLICIT_SELF,
        METHOD_OWNER,
    }
}