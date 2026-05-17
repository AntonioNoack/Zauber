package me.anno.zauber.ast.rich.expression.resolved

import me.anno.zauber.ast.rich.member.Constructor
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.ResolvedConstructor
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.typeresolution.members.ResolvedMember
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.Type

class ResolvedCallExpression(
    self: Expression?,
    val callable: ResolvedMember<*>,
    val valueParameters: List<Expression>,
    scope: Scope, origin: Long
) : Expression(scope, origin) {

    val self: Expression? =
        if (callable is ResolvedConstructor) self as? SuperExpression
        else self ?: callable.getBaseIfMissing(scope, origin)

    init {
        check(valueParameters.all { it.isResolved() })
        when (callable) {
            is ResolvedMethod, is ResolvedField -> {
                // these must have an owner
                check(this.self != null) { "Expected self to not be null for $callable" }
            }
            is ResolvedConstructor -> {
                // in inner classes, self is passed by the first value parameter
                // check(this.self == null) { "Expected self to be null for $callable, got $self" }
                // self may be SuperExpression
            }
            else -> throw NotImplementedError()
        }
    }


    val context get() = callable.context

    override fun clone(scope: Scope) = ResolvedCallExpression(
        self?.clone(scope), callable,
        valueParameters.map { it.clone(scope) },
        scope, origin
    )

    override fun needsBackingField(methodScope: Scope): Boolean {
        return (self != null && self.needsBackingField(methodScope)) ||
                valueParameters.any { it.needsBackingField(methodScope) }
    }

    override fun resolveReturnType(context: ResolutionContext): Type = callable.getTypeFromCall()
    override fun splitsScope(): Boolean = false
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun isResolved(): Boolean = true

    override fun toStringImpl(depth: Int): String {
        val base = if (self != null) "(${self.toString(depth)})." else ""
        val valueParameters = valueParameters.joinToString(", ", "(", ")") { it.toString(depth) }
        val typeParameters = callable.specialization.typeParameters
        val name = when (val m = callable.resolved) {
            is Method -> m.name
            is Field -> m.name
            is Constructor -> m.classScope.name
            else -> throw NotImplementedError()
        }
        return if (typeParameters.isEmpty()) {
            "$base$name$valueParameters"
        } else {
            "$base$name${typeParameters.joinToString(", ", "<", ">")}$valueParameters"
        }
    }

    override fun forEachExpression(callback: (Expression) -> Unit) {
        if (self != null) callback(self)
        for (param in valueParameters) callback(param)
    }

}