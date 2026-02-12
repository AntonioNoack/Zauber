package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.ast.rich.TokenListIndex
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.resolved.ResolvedCallExpression
import me.anno.zauber.ast.rich.expression.resolved.ResolvedGetFieldExpression
import me.anno.zauber.ast.simple.ASTSimplifier.reorderResolveParameters
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.members.FieldResolver
import me.anno.zauber.typeresolution.members.MethodResolver
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.typeresolution.members.ResolvedMember
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
        if (right is DotExpression) {
            throw IllegalArgumentException("List of dot-expressions must be left-to-right, $this is right to left")
        }
    }

    override fun clone(scope: Scope) = DotExpression(
        left.clone(scope), typeParameters,
        right.clone(scope),
        scope, origin
    )

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean {
        val contextI = context
            .withTargetType(null /* unknown */)
        return typeParameters == null ||
                left.hasLambdaOrUnknownGenericsType(contextI) ||
                right.hasLambdaOrUnknownGenericsType(contextI)
    }

    override fun needsBackingField(methodScope: Scope): Boolean {
        return left.needsBackingField(methodScope) ||
                right.needsBackingField(methodScope)
    }

    override fun splitsScope(): Boolean {
        return left.splitsScope() ||
                right.splitsScope()
    }

    override fun isResolved(): Boolean = false

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

    fun getBaseType(context: ResolutionContext): Type {
        return TypeResolution.resolveType(
            /* targetLambdaType seems not easily deductible */
            context.withTargetType(null),
            left,
        )
    }

    fun isFieldType(): Boolean {
        return when (right) {
            is MemberNameExpression,
            is FieldExpression,
            is UnresolvedFieldExpression -> true
            else -> false
        }
    }

    fun isMethodType(): Boolean {
        return right is CallExpression
    }

    fun resolveField(context: ResolutionContext, baseType: Type): ResolvedField? {
        // println("resolveField(): LHS: $baseType, RHS: ${right.javaClass.simpleName}")
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
                    context.withSelfType(baseType), scope,
                    right.name, right.nameAsImport, null, origin,
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
                    context.withSelfType(baseType), scope,
                    right.name, right.nameAsImport, null, origin,
                )
            }
            is FieldExpression -> {
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
                    right.field, null, scope, origin,
                )
            }
            else -> throw NotImplementedError(
                "dot-operator with $right (${right.javaClass.simpleName}) in " +
                        TokenListIndex.resolveOrigin(origin)
            )
        }
    }

    fun resolveCallable(context: ResolutionContext, baseType: Type): ResolvedMember<*> {
        right as CallExpression
        when (val base = right.self) {
            is MemberNameExpression -> {
                val constructor = null
                // todo for lambdas, baseType must be known for their type to be resolved
                val valueParameters = TypeResolution.resolveValueParameters(context, right.valueParameters)
                val context = context.withSelfType(baseType)
                return MethodResolver.resolveCallable(
                    context, scope, base.name, base.nameAsImport, constructor,
                    right.typeParameters, valueParameters, origin,
                ) ?: MethodResolver.printScopeForMissingMethod(
                    context, this, base.name,
                    right.typeParameters, valueParameters
                )
            }
            is UnresolvedFieldExpression -> {
                val constructor = null
                // todo for lambdas, baseType must be known for their type to be resolved
                val valueParameters = TypeResolution.resolveValueParameters(context, right.valueParameters)
                val context = context.withSelfType(baseType)
                return MethodResolver.resolveCallable(
                    context, scope, base.name, base.nameAsImport, constructor,
                    right.typeParameters, valueParameters, origin,
                ) ?: MethodResolver.printScopeForMissingMethod(
                    context, this, base.name,
                    typeParameters, valueParameters
                )
            }
            else -> throw NotImplementedError("Resolve type of call $base (${base.javaClass.simpleName})")
        }
    }

    override fun resolveReturnType(context: ResolutionContext): Type {
        return resolve(context).resolveReturnType(context)
    }

    override fun resolveImpl(context: ResolutionContext): Expression {
        val base = left.resolve(context)
        val baseType = getBaseType(context)
        when {
            isFieldType() -> {
                val field = resolveField(context, baseType)
                    ?: throw IllegalStateException("Unresolved field for field type: (${right.javaClass.simpleName}) $baseType dot $right")
                return ResolvedGetFieldExpression(base, field, scope, origin)
            }
            isMethodType() -> {
                right as CallExpression
                val callable = resolveCallable(context, baseType)
                if (callable.resolved !is MethodLike) {
                    throw IllegalStateException("Implement DotExpression with methodType, but field: $this")
                }
                val targetParams = callable.resolved.valueParameters
                val params = reorderResolveParameters(context, right.valueParameters, targetParams, scope, origin)
                return ResolvedCallExpression(base, callable, params, scope, origin)
            }
            else -> throw NotImplementedError("Resolve DotExpression with type ${right.javaClass.simpleName}")
        }
    }

    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(left)
        callback(right)
    }
}