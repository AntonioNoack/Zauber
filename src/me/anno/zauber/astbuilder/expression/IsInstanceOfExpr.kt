package me.anno.zauber.astbuilder.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType

/**
 * [is, !is]
 * */
class IsInstanceOfExpr(val left: Expression, val right: Type, val negated: Boolean, scope: Scope, origin: Int) :
    Expression(scope, origin) {

    override fun forEachExpr(callback: (Expression) -> Unit) {
        callback(left)
    }

    override fun toString(): String {
        return "($left)${if (negated) "!is" else "is"}($right)"
    }

    override fun resolveType(context: ResolutionContext): Type = BooleanType

    override fun clone(scope: Scope): Expression {
        return IsInstanceOfExpr(left.clone(scope), right, negated, scope, origin)
    }
}