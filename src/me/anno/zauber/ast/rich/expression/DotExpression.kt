package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.TypeResolution.resolveValueParameters
import me.anno.zauber.typeresolution.members.FieldResolver
import me.anno.zauber.typeresolution.members.FieldResolver.resolveFieldType
import me.anno.zauber.typeresolution.members.MethodResolver.resolveCallType
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

/**
 * left.right
 * */
class DotExpression(
    val left: Expression,
    val typeParameters: List<Type>?,
    val right: Expression,
    scope: Scope, origin: Int
) : Expression(scope, origin) {

    init {
        if (right is DotExpression)
            throw IllegalStateException(".-stack must be within base, not in parameter: $this")
    }

    override fun forEachExpr(callback: (Expression) -> Unit) {
        callback(left)
        callback(right)
    }

    override fun clone(scope: Scope) = DotExpression(
        left.clone(scope), typeParameters,
        right.clone(scope),
        scope, origin
    )

    override fun hasLambdaOrUnknownGenericsType(): Boolean {
        return typeParameters == null ||
                left.hasLambdaOrUnknownGenericsType() ||
                right.hasLambdaOrUnknownGenericsType()
    }

    override fun toStringImpl(depth: Int): String {
        val base = left.toString(depth)
        val typeParams = if (typeParameters.isNullOrEmpty()) null else
            typeParameters.joinToString(", ", "<", ">") { it.toString(depth) }
        return if (left is MemberNameExpression || left is FieldExpression || left is DotExpression) {
            "$base$typeParams.${right.toString(depth)}"
        } else {
            "($base)$typeParams.${right.toString(depth)}"
        }
    }

    fun resolveField(context: ResolutionContext): ResolvedField? {
        val baseType = TypeResolution.resolveType(
            /* targetLambdaType seems not easily deductible */
            context.withTargetType(null),
            left,
        )

        when (right) {
            is MemberNameExpression -> {
                // todo replace own generics, because we don't know them yet
                /*val selfType = context.selfType
                val baseType = if (baseType.containsGenerics() && selfType is ClassType) {
                    resolveGenerics(
                        baseType,
                        selfType.clazz.typeParameters,
                        selfType.clazz.typeParameters.map { it.type })
                } else baseType*/
                return FieldResolver.resolveField(
                    context.withSelfType(baseType),
                    right.name, null,
                )
            }
            is UnresolvedFieldExpression -> {
                // todo replace own generics, because we don't know them yet
                /*val selfType = context.selfType
                val baseType = if (baseType.containsGenerics() && selfType is ClassType) {
                    resolveGenerics(
                        baseType,
                        selfType.clazz.typeParameters,
                        selfType.clazz.typeParameters.map { it.type })
                } else baseType*/
                return FieldResolver.resolveField(
                    context.withSelfType(baseType),
                    right.name, null,
                )
            }
            is CallExpression -> return null /* not a field */
            else -> TODO("dot-operator with $right (${right.javaClass.simpleName}) in ${resolveOrigin(origin)}")
        }
    }

    override fun resolveType(context: ResolutionContext): Type {
        val baseType = TypeResolution.resolveType(
            /* targetLambdaType seems not easily deductible */
            context.withTargetType(null),
            left,
        )

        when (right) {
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
                    right.name, null, origin
                )
            }
            is UnresolvedFieldExpression -> {
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
                    right.name, null, origin
                )
            }
            is CallExpression -> {
                when (val base = right.base) {
                    is MemberNameExpression -> {
                        val constructor = null
                        // todo for lambdas, baseType must be known for their type to be resolved
                        val valueParameters = resolveValueParameters(context, right.valueParameters)
                        return resolveCallType(
                            context.withSelfType(baseType),
                            this, base.name, constructor,
                            right.typeParameters, valueParameters
                        )
                    }
                    is UnresolvedFieldExpression -> {
                        val constructor = null
                        // todo for lambdas, baseType must be known for their type to be resolved
                        val valueParameters = resolveValueParameters(context, right.valueParameters)
                        return resolveCallType(
                            context.withSelfType(baseType),
                            this, base.name, constructor,
                            right.typeParameters, valueParameters
                        )
                    }
                    else -> throw NotImplementedError("Resolve type of call $base (${base.javaClass.simpleName})")
                }
            }
            else -> TODO("dot-operator with $right (${right.javaClass.simpleName}) in ${resolveOrigin(origin)}")
        }
    }
}