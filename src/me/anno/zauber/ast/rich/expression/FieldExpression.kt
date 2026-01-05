package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.MemberResolver.Companion.findGenericsForMatch
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class FieldExpression(
    val field: Field,
    scope: Scope, origin: Int
) : Expression(scope, origin) {

    companion object {
        private val LOGGER = LogManager.getLogger(FieldExpression::class)
    }

    override fun forEachExpr(callback: (Expression) -> Unit) {}
    override fun toStringImpl(depth: Int): String = field.toString(depth)
    override fun clone(scope: Scope) = FieldExpression(field, scope, origin)
    override fun hasLambdaOrUnknownGenericsType(): Boolean = false

    override fun resolveType(context: ResolutionContext): Type {
        if (LOGGER.enableInfo) LOGGER.info("FieldExpr.findGenerics(${field.selfType}.${field.name} in context), must return non-null")
        val generics = findGenericsForMatch(
            field.selfType, if (field.selfType == null) null else context.selfType,
            field.valueType, context.targetType,
            emptyList(), emptyParameterList(),
            emptyList(), emptyList()
        )
        check(generics != null) {
            "Resolved field $field, but somehow the generics were incompatible???"
        }
        val resolved = ResolvedField(generics, field, emptyParameterList(), context)
        return resolved.getValueType(context)
    }
}