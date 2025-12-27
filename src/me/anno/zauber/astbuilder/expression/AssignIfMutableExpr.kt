package me.anno.zauber.astbuilder.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.UnitType

/**
 * this.name [+=, *=, /=, ...] right
 * */
class AssignIfMutableExpr(val left: Expression, val symbol: String, val right: Expression) :
    Expression(left.scope, right.origin) {

    override fun forEachExpr(callback: (Expression) -> Unit) {
        callback(left)
        callback(right)
    }

    override fun toString(): String {
        return "$left $symbol $right"
    }

    override fun resolveType(context: ResolutionContext): Type {
        return UnitType
    }

    override fun clone(scope: Scope): Expression = AssignIfMutableExpr(left.clone(scope), symbol, right.clone(scope))
}