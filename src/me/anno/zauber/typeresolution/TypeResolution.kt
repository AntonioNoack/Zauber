package me.anno.zauber.typeresolution

import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.Compile.STDLIB_NAME
import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.parameter.NamedParameter
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.unresolved.ArrayToVarargsStar
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.*
import me.anno.zauber.types.impl.arithmetic.AndType
import me.anno.zauber.types.impl.arithmetic.NotType
import me.anno.zauber.types.impl.arithmetic.NullType
import me.anno.zauber.types.impl.arithmetic.UnionType
import me.anno.zauber.types.impl.unresolved.UnresolvedType

/**
 * Resolve types step by step, might fail, but should be stable at least.
 * */
object TypeResolution {

    private val LOGGER = LogManager.getLogger(TypeResolution::class)

    var catchFailures = false

    fun doCatchFailures() {
        catchFailures = true
        LogManager.disableLoggers(
            "TypeResolution,Inheritance," +
                    "MemberResolver,ConstructorResolver,MethodResolver,FieldResolver," +
                    "ResolvedField,ResolvedMethod,CallExpression,Field,ResolvedCallable," +
                    "LambdaExpression,UnderdefinedValueParameter,FieldExpression"
        )
    }

    // todo make this depend on which language we currently parse
    val langScope by threadLocal { root.getOrPut(STDLIB_NAME, null) }

    fun getSelfType(scope: Scope): Type? {
        // println("Searching selfType for $scope")
        var scope = scope
        while (true) {
            if (scope.isClassLike()) {
                return scope.typeWithArgs
            }

            // if inside method, we need to check method.selfType
            val selfType = scope.selfAsMethod?.selfType
            if (selfType != null) return selfType

            scope = scope.parent ?: return null
        }
    }

    var depth = 0

    /**
     * resolve the type for a given expression;
     * todo expr can be a lambda,
     *  and then the type not only depends on expr, but what it's used for, too,
     *  e.g. List<Int>.map { it * 2 } -> List<Int>.map(Function1<S,T>),
     *  S <= Int, because there is a only a function List<V>.map(Function1<V,R>).
     * */
    fun resolveType(context: ResolutionContext, expr: Expression): Type {
        // if already resolved, just use that type
        LOGGER.info("[${++depth}] Resolving type of (${expr.javaClass.simpleName}) $expr (targetType=${context.targetType})")
        val type = expr.resolveReturnType(context).resolvedName
        LOGGER.info("[${depth--}] Resolved type of $expr to $type (${type.javaClass.simpleName})")
        return type
    }

    fun resolveValueParameters(
        context: ResolutionContext,
        base: List<NamedParameter>,
        selfScope: Scope? = null,
    ): List<ValueParameter> {
        // target-type does not apply to parameters
        val contextI = context.withTargetType(null)
        return base.map { param ->
            val hasVarargStar = param.value is ArrayToVarargsStar
            if (param.value.hasLambdaOrUnknownGenericsType(contextI)) {
                LOGGER.info("Underdefined generics in $param :/")
                UnderdefinedValueParameter(param, contextI, hasVarargStar)
            } else {
                val type = resolveType(contextI, param.value).resolve(selfScope)
                ValueParameterImpl(param.name, type, hasVarargStar)
            }
        }
    }

    fun resolveThisScope(scope0: Scope): Scope {
        var scope = scope0
        while (true) {
            LOGGER.info("Checking ${scope.pathStr}/${scope.scopeType} for 'this'")
            when {
                scope.isClassLike() || scope.isMethodLike() -> return scope
                else -> {}
            }
            scope = scope.parentIfSameFile
                ?: throw IllegalStateException("Failed to resolve 'this' in $scope0")
        }
    }

    fun resolveThisType(context: ResolutionContext, scope: Scope): Type {
        // todo we must also insert any known generics...
        var scopeI = scope
        while (true) {
            LOGGER.info("Checking ${scopeI.pathStr}/${scopeI.scopeType} for 'this'")
            when {
                scopeI.isClassLike() -> {
                    val base = scopeI.typeWithArgs
                    return if (scopeI.isObjectLike()) base else NonObjectClassType(base)
                }
                scopeI.isMethodLike() -> {
                    val func = scopeI.selfAsMethod
                    val self = func?.selfType?.specialize(context)
                    if (self != null) {
                        val selfScope = typeToScope(self)!!
                        LOGGER.info("Method-SelfType[${scopeI.pathStr}/spec=${context.specialization}]: $self -> $selfScope")
                        return resolveThisScope(selfScope).typeWithArgs
                    } else if (func != null) {
                        return scopeI.typeWithArgs
                    }
                }
                else -> {}
            }
            scopeI = scopeI.parent
                ?: throw IllegalStateException("Failed to resolve SelfType in $scope")
        }
    }

    fun findType(
        scope: Scope, // 2nd, recursive as long as fileName == parentScope.fileName
        selfScope: Type?, // 1st, surface-level only
        name: String
    ): Type? = findType(typeToScope(selfScope), name) ?: findType(scope, name)

    fun typeToScope(type: Type?): Scope? {
        return when (type) {
            null, NullType -> null
            is NotType -> null
            is ComptimeValue -> typeToScope(type.type)
            is ClassType -> type.clazz
            is UnionType -> {
                val scopes = type.types.mapNotNull { typeToScope(it) }
                if (scopes.distinct().size == 1) scopes.first()
                else null
            }
            is AndType -> {
                val scopes = type.types.mapNotNull { typeToScope(it) }
                if (scopes.distinct().size == 1) scopes.first()
                else null
            }
            is GenericType -> typeToScope(type.superBounds) // or should we choose null?
            is UnresolvedType -> typeToScope(type.resolve())
            // is NullableType -> typeToScope(type.base)
            else -> throw NotImplementedError("typeToScope($type, ${type.javaClass.simpleName})")
        }
    }

    fun findType(scope: Scope?, name: String): Type? {
        var scope = scope ?: return null
        while (true) {

            val selfMatch = scope.children
                .firstOrNull { it.name == name && it[ScopeInitType.AFTER_DISCOVERY].isClassLike() }

            if (selfMatch != null) {
                val typeParams: List<Type>? =
                    if (selfMatch.hasTypeParameters && selfMatch.typeParameters.isEmpty()) emptyList() else null
                return ClassType(selfMatch, typeParams, -1)
            }

            val genericsMatch = scope.typeParameters.firstOrNull { it.name == name }
            if (genericsMatch != null) return GenericType(genericsMatch.scope, genericsMatch.name)

            scope = scope.parentIfSameFile ?: return null
        }
    }
}