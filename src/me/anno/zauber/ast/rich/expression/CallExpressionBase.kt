package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.NamedParameter
import me.anno.zauber.ast.rich.expression.resolved.ResolvedCallExpression
import me.anno.zauber.ast.rich.expression.resolved.ResolvedGetFieldExpression
import me.anno.zauber.ast.simple.ASTSimplifier.reorderParameters
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution.resolveValueParameters
import me.anno.zauber.typeresolution.members.ResolvedConstructor
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.typeresolution.members.ResolvedMember
import me.anno.zauber.typeresolution.members.ResolvedMethod
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

    companion object {
        private val LOGGER = LogManager.getLogger(CallExpressionBase::class)
    }

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean {
        val contextI = context
            .withTargetType(null /* unknown */)
        if (base.hasLambdaOrUnknownGenericsType(contextI) ||
            valueParameters.any { valueParameter ->
                valueParameter.value.hasLambdaOrUnknownGenericsType(contextI)
            }
        ) return true

        if (typeParameters != null) return false

        try {
            return when (val resolved = resolveCallable(context).resolved) {
                is Method, is Constructor -> resolved.hasUnderdefinedGenerics
                is Field -> true // todo this must be some fun interface -> check whether it has underdefined generics
                else -> throw NotImplementedError("Has $resolved underdefined generics?")
            }
        } catch (e: IllegalStateException) {
            // this can fail, because some values may still be unknown
            // e.printStackTrace()
            LOGGER.warn("Failed in hasLambdaOrUnknownGenericsType: ${e.message}")
            // we cannot be sure, better be safe
            return true
        }
    }

    override fun resolveType(context: ResolutionContext): Type {
        return resolveCallable(context).getTypeFromCall()
    }

    abstract fun resolveCallable(context: ResolutionContext): ResolvedMember<*>
    
    override fun resolveImpl(context: ResolutionContext): Expression {
        val base = base.resolve(context)
        return when (val callable = resolveCallable(context)) {
            is ResolvedMethod -> {
                val params = reorderParameters(valueParameters, callable.resolved.valueParameters, scope, origin)
                ResolvedCallExpression(base, callable, params, scope, origin)
            }
            is ResolvedConstructor -> {
                val params = reorderParameters(valueParameters, callable.resolved.valueParameters, scope, origin)
                ResolvedCallExpression(base, callable, params, scope, origin)
            }
            is ResolvedField -> {
                val valueParameters1 = resolveValueParameters(context, valueParameters)
                val calledMethod = callable.resolveCalledMethod(typeParameters, valueParameters1)
                val params = reorderParameters(valueParameters, calledMethod.resolved.valueParameters, scope, origin)
                val base1 = ResolvedGetFieldExpression(base, callable, scope, origin)
                ResolvedCallExpression(base1, calledMethod, params, scope, origin)
            }
            else -> throw NotImplementedError()
        }
    }
}