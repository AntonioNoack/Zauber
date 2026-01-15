package me.anno.zauber.ast.rich.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType

class CheckEqualsOp(
    val left: Expression, val right: Expression,
    val byPointer: Boolean, val negated: Boolean,
    scope: Scope, origin: Int
) : Expression(scope, origin) {

    val symbol: String
        get() = when {
            byPointer && negated -> "!=="
            byPointer -> "==="
            negated -> "!="
            else -> "=="
        }

    override fun toStringImpl(depth: Int): String {
        return "(${left.toString(depth)})$symbol(${right.toString(depth)})"
    }

    override fun resolveType(context: ResolutionContext): Type = BooleanType
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // result is always Boolean
    override fun needsBackingField(methodScope: Scope): Boolean {
        return left.needsBackingField(methodScope) || right.needsBackingField(methodScope)
    }

    override fun splitsScope(): Boolean {
        return left.splitsScope() || right.splitsScope()
    }

    override fun clone(scope: Scope) =
        CheckEqualsOp(left.clone(scope), right.clone(scope), byPointer, negated, scope, origin)

    override fun isResolved(): Boolean = left.isResolved() && right.isResolved()
    override fun resolveImpl(context: ResolutionContext): Expression {
        return CheckEqualsOp(left.resolve(context), right.resolve(context), byPointer, negated, scope, origin)
    }
}