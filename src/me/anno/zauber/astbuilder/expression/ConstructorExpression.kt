package me.anno.zauber.astbuilder.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

class ConstructorExpression(
    val clazz: Scope,
    val typeParams: List<Type>,
    val params: List<Expression>,
    scope: Scope, origin: Int
) : Expression(scope, origin) {

    override fun forEachExpr(callback: (Expression) -> Unit) {
        for (i in params.indices) {
            callback(params[i])
        }
    }

    override fun toStringImpl(depth: Int): String {
        return "new($clazz)(${params.joinToString { it.toString(depth) }})"
    }

    override fun resolveType(context: ResolutionContext): Type {
        return ClassType(clazz, typeParams)
    }

    override fun clone(scope: Scope) =
        ConstructorExpression(clazz, typeParams, params.map { it.clone(scope) }, scope, origin)
}