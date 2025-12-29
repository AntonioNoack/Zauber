package me.anno.zauber.astbuilder.expression

import me.anno.zauber.astbuilder.Field
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.MemberResolver.Companion.findGenericsForMatch
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class FieldExpression(
    val field: Field,
    scope: Scope, origin: Int
) : Expression(scope, origin) {
    override fun forEachExpr(callback: (Expression) -> Unit) {}
    override fun toString(depth: Int): String = field.toString()
    override fun clone(scope: Scope) = FieldExpression(field, scope, origin)
    override fun hasLambdaOrUnknownGenericsType(): Boolean = false

    override fun resolveType(context: ResolutionContext): Type {
        val generics = findGenericsForMatch(
            field.selfType, if (field.selfType == null) null else context.selfType,
            field.valueType, context.targetType,
            emptyList(), emptyList(),
            emptyList(), emptyList()
        )
        check(generics != null) {
            "Resolved field $field, but somehow the generics were incompatible???"
        }
        val resolved = ResolvedField(generics, field, emptyList(), context)
        return resolved.getValueType(context)
    }
}