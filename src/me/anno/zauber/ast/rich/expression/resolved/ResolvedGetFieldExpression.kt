package me.anno.zauber.ast.rich.expression.resolved

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class ResolvedGetFieldExpression(
    /**
     * if not present, use 'this' or objectField
     * */
    owner: Expression?,
    field: ResolvedField,
    scope: Scope, origin: Int
) : ResolvedFieldExpression(owner, field, scope, origin) {

    override fun resolveType(context: ResolutionContext): Type = field.getValueType()
    override fun clone(scope: Scope): Expression =
        ResolvedGetFieldExpression(owner?.clone(scope), field, scope, origin)

    override fun toStringImpl(depth: Int): String {
        return "$owner.$field"
    }
}