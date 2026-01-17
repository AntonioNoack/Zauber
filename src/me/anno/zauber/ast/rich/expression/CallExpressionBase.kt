package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.ast.rich.expression.resolved.ResolvedCallExpression
import me.anno.zauber.ast.rich.expression.resolved.ResolvedGetFieldExpression
import me.anno.zauber.ast.rich.expression.unresolved.*
import me.anno.zauber.ast.simple.ASTSimplifier.reorderParameters
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution.resolveValueParameters
import me.anno.zauber.typeresolution.members.ResolvedConstructor
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.typeresolution.members.ResolvedMember
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.LambdaType

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

    private fun Expression.isLambdaLike(): Boolean {
        return when (this) {
            is LambdaExpression, is DoubleColonLambda,
            is GetMethodFromTypeExpression, is GetMethodFromValueExpression -> true
            else -> false
        }
    }

    private fun resolveInlineMethod(
        context: ResolutionContext, callable: ResolvedMethod,
        params0: List<Expression>
    ): Expression {
        // define all parameters, that are not lambda-likes
        val method = callable.resolved
        val subscope = scope.generate("inline", ScopeType.METHOD_BODY)
        // todo define 'this' field/parameter in all relevant scopes
        // todo handle 'this' like any other field, and allow labels on any field to denote the scope
        // todo create sub-scope for this, and define all parameters as fields of that sub-scope,
        //  because usually, they would be in the method scope, but here, we don't use that scope
        // todo we must recursively support this replacement, e.g. for inline methods with default parameters
        //  -> register these special lambdas in the context :)
        val body = ArrayList<Expression>()
        val knownLambdas = HashMap(context.knownLambdas)
        val inlineBody = method.body
            ?: throw IllegalStateException(
                "Inline method must have a body, method: $method, " +
                        "at ${resolveOrigin(origin)}"
            )
        for (i in params0.indices) {
            val param = params0[i]
            if (!param.isLambdaLike()) {
                val dstField = method.valueParameters[i].field!!
                val dstFieldExpr = FieldExpression(dstField, subscope, origin)
                body.add(AssignmentExpression(dstFieldExpr, param))
                subscope.addField(dstField)
            } else {
                knownLambdas[method.valueParameters[i].field!!] = param
            }
        }
        body.add(inlineBody)
        val extendedContext = context.withKnownLambdas(knownLambdas)
        // todo if lambdaType has a 'this'/when we have some sort of 'this',
        //  we need to define it as a variable/field...
        println("Inlined body:\n${body.joinToString("\n") { "  $it" }}")
        return ExpressionList(body, scope, origin).resolve(extendedContext)
    }

    private fun resolveInlineInvocation(
        context: ResolutionContext, callable: ResolvedField,
        inlineBody: Expression
    ): Expression {
        val parameter = callable.resolved.byParameter as? Parameter
            ?: throw IllegalStateException(
                "Expected field by lambda to belong to a parameter, " +
                        "field: $callable, at ${resolveOrigin(origin)}"
            )
        val parameterType = parameter.type as LambdaType
        val subscope = scope.generate("inline", ScopeType.METHOD_BODY)
        val body = ArrayList<Expression>()
        when (inlineBody) {
            is LambdaExpression -> {
                val selfType = parameterType.selfType
                if (selfType != null) {
                    // todo define 'this' at inlined place with 'base'

                }
                for (i in parameterType.parameters.indices) {
                    val param = valueParameters[i].value
                    val variable = inlineBody.variables!![i]
                    val dstField = variable.field
                    if (dstField.name == "_") continue // assignment can be skipped

                    val dstFieldExpr = FieldExpression(dstField, subscope, dstField.origin)
                    body.add(AssignmentExpression(dstFieldExpr, param))
                    subscope.addField(dstField)

                    // todo we could run and support this recursively :3
                    if (variable is LambdaDestructuring) {
                        // we must also assign all the child fields
                        val components = variable.components
                        for (i in components.indices) {
                            val component = components[i]
                            if (component.name == "_") continue

                            val dstField = component.field
                            val dstFieldExpr = FieldExpression(dstField, subscope, dstField.origin)
                            val param = NamedCallExpression(
                                dstFieldExpr,
                                "component${i + 1}", subscope, dstField.origin
                            )
                            body.add(AssignmentExpression(dstFieldExpr, param))
                            subscope.addField(dstField)
                        }
                    }
                }
                body.add(inlineBody.body)
            }
            else -> throw NotImplementedError("Implement inlining a call for a lambda-like: $inlineBody (${inlineBody.javaClass.simpleName})")
        }
        return ExpressionList(body, subscope, origin).resolve(context)
    }

    override fun resolveImpl(context: ResolutionContext): Expression {
        return when (val callable = resolveCallable(context)) {
            is ResolvedMethod -> {
                val method = callable.resolved
                val isInlineMethod = method.isInline()
                val params0 = reorderParameters(valueParameters, method.valueParameters, scope, origin)
                // we can only inline, if some or our parameters are lambdas
                //  or lambda-likes... (Type::add) should work, too
                //  -> no, we can always inline :)
                if (isInlineMethod) {
                    resolveInlineMethod(context, callable, params0)
                } else {
                    // todo base must be defined, so resolve instance/this
                    val base = if (base !is FieldExpression && base !is UnresolvedFieldExpression) {
                        base.resolve(context)
                    } else null
                    val params1 = params0.map { it.resolve(context) }
                    ResolvedCallExpression(base, callable, params1, scope, origin)
                }
            }
            is ResolvedConstructor -> {
                check(base is TypeExpression || base is UnresolvedFieldExpression) {
                    "In ResolvedConstructor, base should be a TypeExpression/UFE, but got ${base.javaClass.simpleName}"
                }
                val params0 = reorderParameters(valueParameters, callable.resolved.valueParameters, scope, origin)
                val params1 = params0.map { it.resolve(context) }
                ResolvedCallExpression(null, callable, params1, scope, origin)
            }
            is ResolvedField -> {
                val inlineBody = context.knownLambdas[callable.resolved]
                if (inlineBody != null) {
                    return resolveInlineInvocation(context, callable, inlineBody)
                }

                val base = base.resolve(context)
                val valueParameters1 = resolveValueParameters(context, valueParameters)
                val calledMethod = callable.resolveCalledMethod(typeParameters, valueParameters1)
                val params = reorderParameters(valueParameters, calledMethod.resolved.valueParameters, scope, origin)
                    .map { it.resolve(context) }
                val base1 = ResolvedGetFieldExpression(base, callable, scope, origin)
                ResolvedCallExpression(base1, calledMethod, params, scope, origin)
            }
            else -> throw NotImplementedError()
        }
    }
}