package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.ast.rich.expression.CompareType
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.resolved.ResolvedCallExpression
import me.anno.zauber.ast.rich.expression.resolved.ResolvedCompareOp
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType

class CompareOp(
    val left: Expression,
    val right: Expression,
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

    override fun clone(scope: Scope) = CompareOp(left.clone(scope), right.clone(scope), type)
    override fun splitsScope(): Boolean = left.splitsScope() || right.splitsScope()
    override fun isResolved(): Boolean = false

    override fun resolveImpl(context: ResolutionContext): Expression {
        val left = left.resolve(context)
        val right = right.resolve(context)
        val proxy = NamedCallExpression(left, "compareTo", right, scope, origin)
        val method = (proxy.resolve(context) as ResolvedCallExpression).callable as ResolvedMethod
        return ResolvedCompareOp(left, right, method, type)
    }

    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(left)
        callback(right)
    }
}
