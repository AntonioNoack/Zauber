package me.anno.zauberei.astbuilder.expression

import me.anno.zauberei.typeresolution.ResolutionContext
import me.anno.zauberei.types.Scope
import me.anno.zauberei.types.Type

/**
 * each assignment is the start of a new sub block,
 *   because we know more about types
 *
 * todo -> if it is a field, or a deep constant field,
 *  we somehow need to register this new field definition
 * */
class AssignmentExpression(var variableName: Expression, var newValue: Expression) :
    Expression(newValue.scope, newValue.origin) {

    override fun forEachExpr(callback: (Expression) -> Unit) {
        callback(variableName)
        callback(newValue)
    }

    override fun toString(): String {
        return "$variableName=$newValue"
    }

    override fun hasLambdaOrUnknownGenericsType(): Boolean = false // this has no return type
    override fun resolveType(context: ResolutionContext): Type = exprHasNoType(context)
    override fun clone(scope: Scope): Expression =
        AssignmentExpression(variableName.clone(scope), newValue.clone(scope))
}