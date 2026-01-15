package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.TokenListIndex
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.members.FieldResolver
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class FieldExpression(
    val field: Field,
    scope: Scope, origin: Int
) : Expression(scope, origin) {

    companion object {
        private val LOGGER = LogManager.getLogger(FieldExpression::class)
    }

    override fun toStringImpl(depth: Int): String = field.toString(depth)
    override fun clone(scope: Scope) = FieldExpression(field, scope, origin)
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun isResolved(): Boolean = false // base is missing
    override fun needsBackingField(methodScope: Scope): Boolean {
        return field.originalScope == methodScope && field.name == "field"
    }

    override fun resolveType(context: ResolutionContext): Type {
        if (LOGGER.enableInfo) LOGGER.info("FieldExpr.findGenerics(${field.selfType}.${field.name} in context), must return non-null")
        val scopeSelfType = TypeResolution.getSelfType(scope)
        val fieldReturnType = FieldResolver.getFieldReturnType(scopeSelfType, field, context.targetType)
        val resolved = FieldResolver.findMemberMatch(
            field, fieldReturnType, context.targetType,
            scopeSelfType, null, emptyList(), scope, origin
        ) ?: throw IllegalStateException(
            "Generics could not be resolved for $field at " +
                    TokenListIndex.resolveOrigin(origin)
        )
        return resolved.getValueType()
    }

    override fun splitsScope(): Boolean = false
}