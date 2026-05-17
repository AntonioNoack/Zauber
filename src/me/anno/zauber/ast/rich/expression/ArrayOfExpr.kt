package me.anno.zauber.ast.rich.expression

import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types

class ArrayOfExpr(val values: List<Expression>, val type: Type, scope: Scope, origin: Long) :
    Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return "arrayOf(${values.joinToString(",") { "\n  " + it.toString(depth) }})"
    }

    override fun resolveReturnType(context: ResolutionContext): Type {
        return Types.Array.withTypeParameter(type.specialize(context))
    }

    override fun clone(scope: Scope) = ArrayOfExpr(values.map { it.clone(scope) }, type, scope, origin)
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun splitsScope(): Boolean = false

    override fun needsBackingField(methodScope: Scope): Boolean {
        return values.any { it.needsBackingField(methodScope) }
    }

    override fun isResolved(): Boolean = values.all { it.isResolved() }

    override fun resolveImpl(context: ResolutionContext): Expression {
        val elementType = type.specialize(context)
        val subContext = context
            .withAllowTypeless(false)
            .withTargetType(elementType)
        return ArrayOfExpr(values.map { it.resolve(subContext) }, elementType, scope, origin)
    }

    override fun forEachExpression(callback: (Expression) -> Unit) {
        for (entry in values) callback(entry)
    }
}