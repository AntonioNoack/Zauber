package me.anno.zauber.ast.rich.expression

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
class AssignmentExpression(var variableName: Expression, var newValue: Expression) :
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
}