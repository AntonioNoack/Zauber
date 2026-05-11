package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.TypeExpression
import me.anno.zauber.ast.rich.expression.resolved.ResolvedSetFieldExpression
import me.anno.zauber.ast.rich.expression.resolved.ThisExpression
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.types.Type

/**
 * each assignment is the start of a new sub block,
 *   because we know more about types
 *
 * todo -> if it is a field, or a deep constant field,
 *  we somehow need to register this new field definition
 * */
class AssignmentExpression(val dst: Expression, val src: Expression, val hasValue: Boolean = false) :
    Expression(src.scope, src.origin) {

    override fun toStringImpl(depth: Int): String {
        return "$dst=${src.toString(depth)}"
    }

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // this has no return type
    override fun resolveReturnType(context: ResolutionContext): Type {
        return if (hasValue) resolveField(context).getValueType()
        else exprHasNoType(context)
    }

    override fun needsBackingField(methodScope: Scope): Boolean {
        return dst.needsBackingField(methodScope) ||
                src.needsBackingField(methodScope)
    }

    override fun clone(scope: Scope): Expression =
        AssignmentExpression(dst.clone(scope), src.clone(scope), hasValue)

    // explicit yes
    override fun splitsScope(): Boolean = true
    override fun isResolved(): Boolean = false

    override fun resolveImpl(context: ResolutionContext): Expression {
        val newValue = src.resolve(context)
        when (val dstExpr = dst) {
            is FieldExpression -> {
                val field = dstExpr.resolveField(context)
                val owner = field.resolveOwnerWithoutLeftSide(scope, origin)
                return ResolvedSetFieldExpression(owner, field, newValue, scope, origin)
            }
            is UnresolvedFieldExpression -> {
                val field = dstExpr.resolveField(context)
                    ?: dstExpr.onMissingField()
                val owner = field.resolveOwnerWithoutLeftSide(scope, origin)
                return ResolvedSetFieldExpression(owner, field, newValue, scope, origin)
            }
            is DotExpression if dstExpr.left is ThisExpression && dstExpr.right is FieldResolvable -> {
                val field = dstExpr.right.resolveField(context)
                    ?: throw IllegalStateException("Could not resolve field for ${dstExpr.right}")
                val owner = dstExpr.left
                return ResolvedSetFieldExpression(owner, field, newValue, scope, origin)
            }
            is DotExpression if dstExpr.left is FieldExpression && dstExpr.right is FieldResolvable -> {
                val owner = dstExpr.left.resolve(context)
                val ownerType = owner.resolveReturnType(context)
                val field = dstExpr.right.resolveField(context.withSelfType(ownerType))
                    ?: throw IllegalStateException("Could not resolve field for ${dstExpr.right}")
                check(field.resolved.isMutable || dstExpr.left.field.isMutableEx) {
                    "Expected ${dstExpr.left.field}.${field.resolved.name} to be mutable @${resolveOrigin(origin)}"
                }
                return ResolvedSetFieldExpression(owner, field, newValue, scope, origin)
            }
            is DotExpression if dstExpr.left is TypeExpression && dstExpr.right is FieldResolvable -> {
                val owner = dstExpr.left.resolve(context)
                val ownerType = owner.resolveReturnType(context)
                val field = dstExpr.right.resolveField(context.withSelfType(ownerType))
                    ?: throw IllegalStateException("Could not resolve field for ${dstExpr.right}")
                check(field.resolved.isMutable) {
                    "Expected ${dstExpr.left}.${field.resolved.name} to be mutable @${resolveOrigin(origin)}"
                }
                return ResolvedSetFieldExpression(owner, field, newValue, scope, origin)
            }
            is DotExpression -> {
                throw NotImplementedError(
                    "Implement assignment to DotExpression (" +
                            "${dstExpr.left.javaClass.simpleName} . " +
                            "${dstExpr.right.javaClass.simpleName})"
                )
            }
            else -> throw NotImplementedError("Implement assignment to $dst (${dst.javaClass.simpleName})")
        }
    }

    fun resolveField(context: ResolutionContext): ResolvedField {
        return when (val dstExpr = dst) {
            is FieldResolvable -> {
                dstExpr.resolveField(context)
                    ?: dstExpr.onMissingField()
            }
            is DotExpression if dstExpr.left is ThisExpression && dstExpr.right is FieldResolvable -> {
                dstExpr.right.resolveField(context)
                    ?: throw IllegalStateException("Could not resolve field for ${dstExpr.right}")
            }
            is DotExpression if dstExpr.left is FieldExpression && dstExpr.right is FieldResolvable -> {
                val owner = dstExpr.left.resolve(context)
                val ownerType = owner.resolveReturnType(context)
                dstExpr.right.resolveField(context.withSelfType(ownerType))
                    ?: throw IllegalStateException("Could not resolve field for ${dstExpr.right}")
            }
            is DotExpression if dstExpr.left is TypeExpression && dstExpr.right is FieldResolvable -> {
                val owner = dstExpr.left.resolve(context)
                val ownerType = owner.resolveReturnType(context)
                dstExpr.right.resolveField(context.withSelfType(ownerType))
                    ?: throw IllegalStateException("Could not resolve field for ${dstExpr.right}")
            }
            is DotExpression -> {
                throw NotImplementedError(
                    "Implement assignment to DotExpression (" +
                            "${dstExpr.left.javaClass.simpleName} . " +
                            "${dstExpr.right.javaClass.simpleName})"
                )
            }
            else -> throw NotImplementedError("Implement assignment to $dst (${dst.javaClass.simpleName})")
        }
    }


    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(dst)
        callback(src)
    }
}