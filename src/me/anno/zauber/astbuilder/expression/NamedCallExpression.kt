package me.anno.zauber.astbuilder.expression

import me.anno.zauber.astbuilder.NamedParameter
import me.anno.zauber.astbuilder.TokenListIndex.resolveOrigin
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.TypeResolution.resolveValueParameters
import me.anno.zauber.typeresolution.members.FieldResolver.resolveFieldType
import me.anno.zauber.typeresolution.members.MethodResolver.resolveCallType
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class NamedCallExpression(
    val base: Expression,
    val name: String,
    val typeParameters: List<Type>?,
    val valueParameters: List<NamedParameter>,
    scope: Scope, origin: Int
) : Expression(scope, origin) {

    init {
        check(name != "?.")
        if (name == "." && valueParameters.size == 1 &&
            valueParameters[0].value is NamedCallExpression
        ) throw IllegalStateException("NamedCall-stack must be within base, not in parameter: $this")
    }

    override fun forEachExpr(callback: (Expression) -> Unit) {
        callback(base)
        for (i in valueParameters.indices) {
            callback(valueParameters[i].value)
        }
    }

    override fun clone(scope: Scope) = NamedCallExpression(
        base.clone(scope), name, typeParameters,
        valueParameters.map { NamedParameter(it.name, it.value.clone(scope)) },
        scope, origin
    )

    override fun hasLambdaOrUnknownGenericsType(): Boolean {
        return typeParameters == null ||
                base.hasLambdaOrUnknownGenericsType() ||
                valueParameters.any { it.value.hasLambdaOrUnknownGenericsType() }
    }

    override fun toStringImpl(depth: Int): String {
        val base = base.toString(depth)
        return if (typeParameters.isNullOrEmpty() && name == "." &&
            valueParameters.size == 1 &&
            when (valueParameters[0].value) {
                is MemberNameExpression,
                is CallExpression,
                is NamedCallExpression -> true
                else -> false
            }
        ) {
            if (this.base is MemberNameExpression) {
                "$base.${valueParameters[0].value.toString(depth)}"
            } else {
                "($base).${valueParameters[0].value.toString(depth)}"
            }
        } else {
            val valueParameters = valueParameters.joinToString(", ", "(", ")") { it.toString(depth) }
            if (typeParameters != null && typeParameters.isEmpty()) {
                "($base).$name$valueParameters"
            } else {
                "($base).$name<${typeParameters?.joinToString() ?: "?"}>$valueParameters"
            }
        }
    }

    override fun resolveType(context: ResolutionContext): Type {
        val baseType = TypeResolution.resolveType(
            /* targetLambdaType seems not easily deductible */
            context.withTargetType(null),
            base,
        )
        if (name == ".") {
            check(valueParameters.size == 1)
            val parameter0 = valueParameters[0]
            check(parameter0.name == null)
            when (val parameter = parameter0.value) {
                is MemberNameExpression -> {
                    // todo replace own generics, because we don't know them yet
                    /*val selfType = context.selfType
                    val baseType = if (baseType.containsGenerics() && selfType is ClassType) {
                        resolveGenerics(
                            baseType,
                            selfType.clazz.typeParameters,
                            selfType.clazz.typeParameters.map { it.type })
                    } else baseType*/
                    return resolveFieldType(
                        context.withSelfType(baseType),
                        parameter.name, null, origin
                    )
                }
                is LazyFieldOrTypeExpression -> {
                    // todo replace own generics, because we don't know them yet
                    /*val selfType = context.selfType
                    val baseType = if (baseType.containsGenerics() && selfType is ClassType) {
                        resolveGenerics(
                            baseType,
                            selfType.clazz.typeParameters,
                            selfType.clazz.typeParameters.map { it.type })
                    } else baseType*/
                    return resolveFieldType(
                        context.withSelfType(baseType),
                        parameter.name, null, origin
                    )
                }
                is CallExpression -> {
                    when (val baseName = parameter.base) {
                        is MemberNameExpression -> {
                            val constructor = null
                            // todo for lambdas, baseType must be known for their type to be resolved
                            val valueParameters = resolveValueParameters(context, parameter.valueParameters)
                            return resolveCallType(
                                context.withSelfType(baseType),
                                this, baseName.name, constructor,
                                parameter.typeParameters, valueParameters
                            )
                        }
                        is LazyFieldOrTypeExpression -> {
                            val constructor = null
                            // todo for lambdas, baseType must be known for their type to be resolved
                            val valueParameters = resolveValueParameters(context, parameter.valueParameters)
                            return resolveCallType(
                                context.withSelfType(baseType),
                                this, baseName.name, constructor,
                                parameter.typeParameters, valueParameters
                            )
                        }
                        else -> throw NotImplementedError()
                    }
                }
                else -> TODO("dot-operator with $parameter (${parameter.javaClass.simpleName}) in ${resolveOrigin(origin)}")
            }
        } else {

            val calleeType = TypeResolution.resolveType(
                /* target lambda type seems not deductible */
                context.withTargetType(null),
                base,
            )
            // todo type-args may be needed for type resolution
            val valueParameters = resolveValueParameters(context, valueParameters)

            val constructor = null
            return resolveCallType(
                context.withSelfType(calleeType),
                this, name, constructor,
                typeParameters, valueParameters
            )
        }
    }
}