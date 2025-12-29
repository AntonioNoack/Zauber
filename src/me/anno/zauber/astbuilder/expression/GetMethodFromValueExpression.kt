package me.anno.zauber.astbuilder.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

/**
 * Generates a lambda from the base, effectively being a::b -> { a.b(allParamsNeeded) }
 * */
class GetMethodFromValueExpression(val base: Expression, val name: String, origin: Int) :
    Expression(base.scope, origin) {

    override fun forEachExpr(callback: (Expression) -> Unit) {}
    override fun toString(depth: Int): String {
        return "${base.toString(depth - 1)}::$name"
    }

    override fun resolveType(context: ResolutionContext): Type {
        // todo resolve method, then resolve lambdaType from that
        TODO("Not yet implemented")
    }

    override fun clone(scope: Scope) = GetMethodFromValueExpression(base.clone(scope), name, origin)

}