package me.anno.zauber.astbuilder.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

class ConstructorExpression(
    val clazz: Scope,
    val typeParameters: List<Type>?,
    val valueParameters: List<Expression>,
    scope: Scope, origin: Int
) : Expression(scope, origin) {

    override fun forEachExpr(callback: (Expression) -> Unit) {
        for (i in valueParameters.indices) {
            callback(valueParameters[i])
        }
    }

    override fun toStringImpl(depth: Int): String {
        return "new($clazz)(${valueParameters.joinToString { it.toString(depth) }})"
    }

    override fun hasLambdaOrUnknownGenericsType(): Boolean {
        return typeParameters?.any { it.containsGenerics() } ?: clazz.typeParameters.isNotEmpty()
    }

    override fun resolveType(context: ResolutionContext): Type {
        return ClassType(clazz, typeParameters)
    }

    override fun clone(scope: Scope) =
        ConstructorExpression(clazz, typeParameters, valueParameters.map { it.clone(scope) }, scope, origin)
}