package me.anno.zauber.astbuilder.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType

class CompareOp(val value: Expression, val type: CompareType) : Expression(value.scope, value.origin) {
    override fun forEachExpr(callback: (Expression) -> Unit) {
        callback(value)
    }

    override fun toString(): String {
        return "($value) ${type.symbol} 0"
    }

    override fun resolveType(context: ResolutionContext): Type {
        return BooleanType
    }

    override fun hasLambdaOrUnknownGenericsType(): Boolean = false // return type is always Boolean

    override fun clone(scope: Scope) = CompareOp(value.clone(scope), type)
}