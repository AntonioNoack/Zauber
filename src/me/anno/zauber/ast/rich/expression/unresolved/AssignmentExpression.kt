package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.resolved.ResolvedSetFieldExpression
import me.anno.zauber.ast.rich.expression.resolved.ThisExpression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

/**
 * each assignment is the start of a new sub block,
 *   because we know more about types
 *
 * todo -> if it is a field, or a deep constant field,
 *  we somehow need to register this new field definition
 * */
class AssignmentExpression(val dst: Expression, val src: Expression) :
    Expression(src.scope, src.origin) {

    override fun toStringImpl(depth: Int): String {
        return "$dst=${src.toString(depth)}"
    }

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // this has no return type
    override fun resolveType(context: ResolutionContext): Type = exprHasNoType(context)
    override fun needsBackingField(methodScope: Scope): Boolean {
        return dst.needsBackingField(methodScope) ||
                src.needsBackingField(methodScope)
    }

    override fun clone(scope: Scope): Expression =
        AssignmentExpression(dst.clone(scope), src.clone(scope))

    // explicit yes
    override fun splitsScope(): Boolean = true
    override fun isResolved(): Boolean = false

    override fun resolveImpl(context: ResolutionContext): Expression {
        val newValue = src.resolve(context)
        when (val dstExpr = dst) {
            is FieldExpression -> {
                val field = dstExpr.resolveField(context)
                val owner = field.resolveOwnerWithoutLeftSide(origin)
                return ResolvedSetFieldExpression(owner, field, newValue, scope, origin)
            }
            is UnresolvedFieldExpression -> {
                val field = dstExpr.resolveField(context)
                val owner = field.resolveOwnerWithoutLeftSide(origin)
                return ResolvedSetFieldExpression(owner, field, newValue, scope, origin)
            }
            is DotExpression if dstExpr.left is ThisExpression && dstExpr.right is FieldExpression -> {
                val field = dstExpr.right.resolveField(context)
                val owner = dstExpr.left
                return ResolvedSetFieldExpression(owner, field, newValue, scope, origin)
            }
            else -> throw NotImplementedError("Implement assignment to $dst (${dst.javaClass.simpleName})")
        }
    }

    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(dst)
        callback(src)
    }
}