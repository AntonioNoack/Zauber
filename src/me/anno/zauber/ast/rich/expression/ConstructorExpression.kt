package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.NamedParameter
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution.resolveValueParameters
import me.anno.zauber.typeresolution.members.ConstructorResolver
import me.anno.zauber.typeresolution.members.ResolvedConstructor
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class ConstructorExpression(
    val clazz: Scope,
    val typeParameters: List<Type>?,
    val valueParameters: List<NamedParameter>,
    val selfIfInsideConstructor: Boolean?,
    scope: Scope, origin: Int
) : Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return "new($clazz)(${valueParameters.joinToString { it.toString(depth) }})"
    }

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean {
        return typeParameters?.any { it.containsGenerics() } ?: clazz.typeParameters.isNotEmpty()
    }

    override fun resolveType(context: ResolutionContext): Type {
        return resolveMethod(context).getTypeFromCall()
    }

    override fun needsBackingField(methodScope: Scope): Boolean {
        return valueParameters.any { it.value.needsBackingField(methodScope) }
    }

    override fun clone(scope: Scope) = ConstructorExpression(
        clazz, typeParameters, valueParameters.map { it.clone(scope) },
        selfIfInsideConstructor, scope, origin
    )

    fun resolveMethod(context: ResolutionContext): ResolvedConstructor {
        val valueParameters = resolveValueParameters(context, valueParameters)
        return ConstructorResolver.findMemberInScope(
            clazz, origin, clazz.name, context.targetType,
            null, typeParameters, valueParameters
        ) ?: throw IllegalStateException("Missing constructor $clazz($valueParameters)")
    }
}