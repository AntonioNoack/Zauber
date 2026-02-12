package me.anno.zauber.ast.rich.expression.resolved

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class ResolvedSetFieldExpression(
    /**
     * if not present, use 'this' or objectField
     * */
    owner: Expression,
    field: ResolvedField,
    val value: Expression,
    scope: Scope, origin: Int
) : ResolvedFieldExpression(owner, field, scope, origin) {

    override fun resolveReturnType(context: ResolutionContext): Type = exprHasNoType(context)
    override fun clone(scope: Scope): Expression =
        ResolvedSetFieldExpression(self.clone(scope), field, value.clone(scope), scope, origin)

    override fun toStringImpl(depth: Int): String {
        return "$self.$field=$value"
    }

    override fun isResolved(): Boolean = super.isResolved() && value.isResolved()

    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(self)
        callback(value)
    }
}