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

    init {
        check(valueParameters.all { it.isResolved() })
    }

    val self = self ?: callable.getBaseIfMissing(scope, origin)
    val context get() = callable.context

    override fun clone(scope: Scope) = ResolvedCallExpression(
        self.clone(scope), callable,
        valueParameters.map { it.clone(scope) },
        scope, origin
    )

    override fun needsBackingField(methodScope: Scope): Boolean {
        return self.needsBackingField(methodScope) ||
                valueParameters.any { it.needsBackingField(methodScope) }
    }

    override fun resolveReturnType(context: ResolutionContext): Type = callable.getTypeFromCall()
    override fun splitsScope(): Boolean = false
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun isResolved(): Boolean = true

    override fun toStringImpl(depth: Int): String {
        val base = self.toString(depth)
        val valueParameters = valueParameters.joinToString(", ", "(", ")") { it.toString(depth) }
        val typeParameters = callable.callTypeParameters
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
        callback(self)
        for (param in valueParameters) callback(param)
    }

}