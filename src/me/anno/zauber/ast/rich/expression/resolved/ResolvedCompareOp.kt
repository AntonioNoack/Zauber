package me.anno.zauber.ast.rich.expression.resolved

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.CompareType
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType

class ResolvedCompareOp(
    val left: Expression,
    val right: Expression,
    val callable: ResolvedMethod,
    val type: CompareType,
) : Expression(left.scope, left.origin) {

    override fun toStringImpl(depth: Int): String {
        return "(${left.toString(depth)} ${type.symbol} ${right.toString(depth)})"
    }

    override fun resolveReturnType(context: ResolutionContext): Type = BooleanType

    /** return type is always Boolean*/
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun needsBackingField(methodScope: Scope): Boolean = left.needsBackingField(methodScope) ||
            right.needsBackingField(methodScope)

    override fun clone(scope: Scope) = ResolvedCompareOp(left.clone(scope), right.clone(scope), callable, type)
    override fun splitsScope(): Boolean = false // is resolved -> no reason to split
    override fun isResolved(): Boolean = true
    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(left)
        callback(right)
    }
}
