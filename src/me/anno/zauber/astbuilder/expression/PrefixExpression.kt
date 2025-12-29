package me.anno.zauber.astbuilder.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class PrefixExpression(val type: PrefixType, origin: Int, val base: Expression) : Expression(base.scope, origin) {

    override fun forEachExpr(callback: (Expression) -> Unit) {
        callback(base)
    }

    override fun toString(depth: Int): String {
        return "${type.symbol}${base.toString(depth - 1)}"
    }

    override fun resolveType(context: ResolutionContext): Type {
        return TypeResolution.resolveType(context, base)
    }

    override fun hasLambdaOrUnknownGenericsType(): Boolean {
        return base.hasLambdaOrUnknownGenericsType()
    }

    override fun clone(scope: Scope) = PrefixExpression(type, origin, base.clone(scope))
}