package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.rich.TokenListIndex
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.resolved.ResolvedGetFieldExpression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.members.FindMemberMatch
import me.anno.zauber.typeresolution.members.FieldResolver
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.types.Type

/**
 * Represents a field in scope.
 * The scope is important for finding out potential casts.
 * */
class FieldExpression(
    val field: Field,
    scope: Scope, origin: Long
) : Expression(scope, origin), FieldResolvable {

    companion object {
        private val LOGGER = LogManager.getLogger(FieldExpression::class)
    }

    init {
        check(scope.isVisibleFrom(field.ownerScope)) {
            "$field is not visible from $scope, expr cannot be created"
        }
    }

    override fun toStringImpl(depth: Int): String = field.toString(depth)
    override fun clone(scope: Scope) = FieldExpression(field, scope, origin)
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun isResolved(): Boolean = false // base is missing
    override fun needsBackingField(methodScope: Scope): Boolean = field.isBackingField(methodScope)

    override fun resolveField(context: ResolutionContext): ResolvedField {
        if (LOGGER.isInfoEnabled) LOGGER.info("FieldExpr.findGenerics(${field.selfType ?: field.ownerScope}.${field.name} in context), must return non-null")
        val scopeSelfType = TypeResolution.getSelfType(scope)
        val fieldReturnType = FieldResolver.getFieldReturnType(scopeSelfType, field, context.targetType)
        return FindMemberMatch.findMemberMatch(
            field, fieldReturnType, context.targetType,
            scopeSelfType, null, emptyList(), context.specialization, scope, origin
        ) as? ResolvedField ?: throw IllegalStateException(
            "Generics could not be resolved for $field at " +
                    TokenListIndex.resolveOrigin(origin)
        )
    }

    override fun resolveReturnType(context: ResolutionContext): Type {
        return resolveField(context).getValueType()
    }

    override fun resolveImpl(context: ResolutionContext): Expression {
        val field = resolveField(context)
        val owner = field.resolveOwnerWithoutLeftSide(scope, origin)
        return ResolvedGetFieldExpression(owner, field, scope, origin)
    }

    override fun splitsScope(): Boolean = false
    override fun forEachExpression(callback: (Expression) -> Unit) {}
}