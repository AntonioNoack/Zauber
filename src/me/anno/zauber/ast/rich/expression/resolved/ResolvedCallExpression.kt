package me.anno.zauber.ast.rich.expression.resolved

import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.ResolvedMember
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class ResolvedCallExpression(
    self: Expression?,
    val callable: ResolvedMember<*>,
    val valueParameters: List<Expression>,
    scope: Scope, origin: Int
) : Expression(scope, origin) {

    val self = self ?: callable.getBaseIfMissing(scope, origin)
    val context get() = callable.context

    override fun clone(scope: Scope) = ResolvedCallExpression(
        this@ResolvedCallExpression.self.clone(scope), callable,
        valueParameters.map { it.clone(scope) },
        scope, origin
    )

    override fun needsBackingField(methodScope: Scope): Boolean {
        return this@ResolvedCallExpression.self.needsBackingField(methodScope) ||
                valueParameters.any { it.needsBackingField(methodScope) }
    }

    override fun resolveType(context: ResolutionContext): Type = callable.getTypeFromCall()
    override fun splitsScope(): Boolean = false
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun isResolved(): Boolean = true

    override fun toStringImpl(depth: Int): String {
        val base = this@ResolvedCallExpression.self.toString(depth)
        val valueParameters = valueParameters.joinToString(", ", "(", ")") { it.toString(depth) }
        val typeParameters = callable.callTypes
        val name = when (val m = callable.resolved) {
            is Method -> m.name
            is Field -> m.name
            is Constructor -> "<new>"
            else -> throw NotImplementedError()
        }
        return if (typeParameters.isEmpty()) {
            "($base).$name$valueParameters"
        } else {
            "($base).$name${typeParameters.joinToString(", ", "<", ">")}$valueParameters"
        }
    }

    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(this@ResolvedCallExpression.self)
        for (param in valueParameters) callback(param)
    }

}