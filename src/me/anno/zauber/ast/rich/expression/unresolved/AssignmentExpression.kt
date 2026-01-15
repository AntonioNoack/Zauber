package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.resolved.ResolvedSetFieldExpression
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
class AssignmentExpression(val variableName: Expression, val newValue: Expression) :
    Expression(newValue.scope, newValue.origin) {

    override fun toStringImpl(depth: Int): String {
        return "$variableName=${newValue.toString(depth)}"
    }

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // this has no return type
    override fun resolveType(context: ResolutionContext): Type = exprHasNoType(context)
    override fun needsBackingField(methodScope: Scope): Boolean {
        return variableName.needsBackingField(methodScope) ||
                newValue.needsBackingField(methodScope)
    }

    override fun clone(scope: Scope): Expression =
        AssignmentExpression(variableName.clone(scope), newValue.clone(scope))

    // explicit yes
    override fun splitsScope(): Boolean = true
    override fun isResolved(): Boolean = false

    override fun resolveImpl(context: ResolutionContext): Expression {
        val newValue = newValue.resolve(context)
        when (val dstExpr = variableName) {
            is FieldExpression -> {
                // todo resolve owner
                val field = dstExpr.resolveField(context)
                return ResolvedSetFieldExpression(null, field, newValue, scope, origin)
            }
            is UnresolvedFieldExpression -> {
                // todo resolve owner
                val field = dstExpr.resolveField(context)
                return ResolvedSetFieldExpression(null, field, newValue, scope, origin)
            }
            else -> throw NotImplementedError("Implement assignment to $variableName (${variableName.javaClass.simpleName})")
        }
    }
}