package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.NamedParameter
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.ResolvedMember
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

/**
 * Calls base[.name]<typeParams>(valueParams)
 * */
abstract class CallExpressionBase(
    val base: Expression,
    val typeParameters: List<Type>?,
    val valueParameters: List<NamedParameter>,
    scope: Scope, origin: Int
) : Expression(scope, origin) {

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean {
        val contextI = context
            .withCodeScope(scope)
            .withTargetType(null /* unknown */)
        if (base.hasLambdaOrUnknownGenericsType(contextI) ||
            valueParameters.any { valueParameter ->
                valueParameter.value.hasLambdaOrUnknownGenericsType(contextI)
            }
        ) return true

        if (typeParameters != null) return false

        return when (val resolved = resolveCallable(context).resolved) {
            is Method, is Constructor -> resolved.hasUnderdefinedGenerics
            is Field -> true // todo this must be some fun interface -> check whether it has underdefined generics
            else -> throw NotImplementedError("Has $resolved underdefined generics?")
        }
    }

    override fun resolveType(context: ResolutionContext): Type {
        return resolveCallable(context).getTypeFromCall()
    }

    abstract fun resolveCallable(context: ResolutionContext): ResolvedMember<*>

}