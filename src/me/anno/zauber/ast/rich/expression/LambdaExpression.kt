package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Keywords
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.LambdaParameter
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.LambdaType

class LambdaExpression(
    var variables: List<LambdaVariable>?,
    val bodyScope: Scope,
    val body: Expression,
) : Expression(bodyScope, body.origin) {

    companion object {
        private val LOGGER = LogManager.getLogger(LambdaExpression::class)
    }

    override fun toStringImpl(depth: Int): String {
        return "LambdaExpr(${variables ?: "?"} -> ${body.toString(depth)})"
    }

    // scope cannot be changed that easily, like with branches and loops
    override fun clone(scope: Scope) = LambdaExpression(variables, bodyScope, body.clone(bodyScope))

    override fun hasLambdaOrUnknownGenericsType(): Boolean = true

    override fun resolveType(context: ResolutionContext): Type {
        LOGGER.info("Handling lambda expression... target: ${context.targetType}")
        val bodyContext = context
            .withCodeScope(bodyScope)
            .withTargetType(null)
        when (val targetLambdaType = context.targetType) {
            is LambdaType -> {
                // automatically add it...
                if (variables == null) {
                    variables = when (val size = targetLambdaType.parameters.size) {
                        0 -> emptyList()
                        1 -> {
                            // define 'it'-parameter in the scope
                            val param0 = targetLambdaType.parameters[0]
                            val type = param0.type
                            val autoParamName = "it"
                            LOGGER.info("Inserting $autoParamName into lambda automatically, type: $type")
                            Field(
                                bodyScope, null, false, param0,
                                autoParamName, type, null,
                                Keywords.NONE, origin
                            )
                            listOf(LambdaVariable(type, autoParamName))
                        }
                        else -> {
                            // instead of throwing, we should probably just return some impossible type or error type...
                            throw IllegalStateException("Found lambda without parameters, but expected $size")
                        }
                    }
                }

                check(variables?.size == targetLambdaType.parameters.size)

                var bodyContext = bodyContext
                val newScopeType = targetLambdaType.scopeType // todo replace generics inside this one?
                if (newScopeType != null) bodyContext = bodyContext.withSelfType(newScopeType)

                val newReturnType = if (targetLambdaType.returnType.containsGenerics()) {
                    // we need to inspect the contents
                    TypeResolution.resolveType(bodyContext, body)
                } else targetLambdaType.returnType // trust-me-bro
                val newParameters = variables!!.mapIndexed { index, param ->
                    val type = param.type ?: targetLambdaType.parameters[index].type
                    LambdaParameter(param.name, type)
                }
                return LambdaType(newScopeType, newParameters, newReturnType)
            }
            null -> {
                // else 'it' is not defined
                if (variables == null) variables = emptyList()

                val returnType = TypeResolution.resolveType(bodyContext, body)
                return LambdaType(null, variables!!.map {
                    LambdaParameter(it.name, it.type!!)
                }, returnType)
            }
            else -> throw NotImplementedError("Extract LambdaType from $targetLambdaType")
        }
    }
}