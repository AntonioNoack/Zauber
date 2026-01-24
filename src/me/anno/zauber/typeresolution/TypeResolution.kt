package me.anno.zauber.typeresolution

import me.anno.zauber.Compile
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.NamedParameter
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.unresolved.ArrayToVarargsStar
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.members.MethodResolver.getMethodReturnType
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.*
import me.anno.zauber.utils.NumberUtils.f1
import kotlin.math.max

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

    val langScope by lazy { Compile.root.getOrPut("zauber", null) }
    var numSuccesses = 0
    var numFailures = 0

    // todo only resolve them where necessary ->
    //  do not call this method in the future...
    fun resolveTypesAndNames(root: Scope) {
        resetStats()
        root.forEachScope(::resolveTypesAndNamesImpl)
        if (LOGGER.enableInfo) printStats()
    }

    private fun resetStats() {
        numSuccesses = 0
        numFailures = 0
    }

    private fun printStats() {
        val successRate = (numSuccesses * 100f) / max(numSuccesses + numFailures, 1)
        LOGGER.info("Resolved fields and methods, $numSuccesses successes (${successRate.f1()}%)")
    }

    private fun isInsideLambda(scope: Scope): Boolean {
        var scope = scope
        while (true) {
            if (scope.scopeType == ScopeType.LAMBDA) return true
            scope = scope.parent ?: return false
        }
    }

    fun resolveTypesAndNamesImpl(scope: Scope) {
        if (isInsideLambda(scope)) {
            // todo parameters usually depend on the context
            return
        }

        val scopeSelfType = getSelfType(scope)
        val children = scope.children
        for (i in children.indices) {
            val method = children[i].selfAsMethod ?: continue
            resolveMethod(scope, scopeSelfType, method)
        }
        for (field in scope.fields) {
            resolveField(scope, scopeSelfType, field)
        }
        if (false) LOGGER.info("${scope.fileName}: ${scope.pathStr}, ${scope.fields.size}f, ${scope.methods.size}m, ${scope.code.size}c")
    }

    private fun resolveMethod(scope: Scope, scopeSelfType: Type?, method: Method) {
        if (method.scope != scope) return // just inherited
        try {
            getMethodReturnType(scopeSelfType, method)
            numSuccesses++
        } catch (e: Throwable) {
            if (!catchFailures) throw e
            // e.printStackTrace()
            numFailures++
            // continue anyway for now
        }
    }

    private fun resolveField(scope: Scope, scopeSelfType: Type?, field: Field) {
        if (field.codeScope != scope) return // just inherited
        if (field.valueType != null) return // done already
        if (field.initialValue == null && field.getterExpr == null) return // cannot be solved

        if (LOGGER.enableInfo) {
            LOGGER.info("Resolving field $field in scope ${scope.pathStr}")
            LOGGER.info("  fieldSelfType: ${field.selfType}, scopeSelfType: $scopeSelfType")
        }
        try {
            val selfType = if (field.explicitSelfType) field.selfType else null
            val higherSelves = if (scopeSelfType != null) {
                mapOf(field.codeScope to scopeSelfType)
            } else emptyMap()
            val context = ResolutionContext(selfType, higherSelves, false, null, emptyMap())
            field.valueType = field.resolveValueType(context)
            if (LOGGER.enableInfo) LOGGER.info("Resolved $field to ${field.valueType}")
            numSuccesses++
        } catch (e: Throwable) {
            if (!catchFailures) throw e
            // e.printStackTrace()
            numFailures++
            // continue anyway for now
        }
    }

    fun getSelfType(scope: Scope): Type? {
        // println("Searching selfType for $scope")
        var scope = scope
        while (true) {
            if (scope.isClassType()) {
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
        val alreadyResolved = expr.resolvedType
        if (alreadyResolved != null) {
            return alreadyResolved
        } else {
            LOGGER.info("[${++depth}] Resolving type of (${expr.javaClass.simpleName}) $expr (targetType=${context.targetType})")
            val type = expr.resolveType(context)
            LOGGER.info("[${depth--}] Resolved type of $expr to $type")
            expr.resolvedType = type
            return type
        }
    }

    fun resolveValueParameters(
        context: ResolutionContext,
        base: List<NamedParameter>
    ): List<ValueParameter> {
        // target-type does not apply to parameters
        val contextI = context.withTargetType(null)
        return base.map { param ->
            val hasVarargStar = param.value is ArrayToVarargsStar
            if (param.value.hasLambdaOrUnknownGenericsType(contextI)) {
                LOGGER.info("Underdefined generics in $param :/")
                UnderdefinedValueParameter(param, contextI, hasVarargStar)
            } else {
                val type = resolveType(contextI, param.value)
                ValueParameterImpl(param.name, type, hasVarargStar)
            }
        }
    }

    fun resolveThisScope(scope: Scope): Scope {
        var scope = scope
        while (true) {
            LOGGER.info("Checking ${scope.pathStr}/${scope.scopeType} for 'this'")
            when {
                scope.isClassType() -> return scope
                scope.scopeType == ScopeType.METHOD -> {
                    val func = scope.selfAsMethod!!
                    val self = func.selfType
                    if (self != null) {
                        val selfScope = typeToScope(self)!!
                        LOGGER.info("Method-SelfType[${scope.pathStr}]: $self -> $selfScope")
                        return resolveThisScope(selfScope)
                    }
                }
                else -> {}
            }
            scope = scope.parent!!
        }
    }

    fun resolveThisType(context: ResolutionContext, scope: Scope): Type {
        // todo we must also insert any known generics...
        var scopeI = scope
        while (true) {
            LOGGER.info("Checking ${scopeI.pathStr}/${scopeI.scopeType} for 'this'")
            when {
                scopeI.isClassType() ||
                        scopeI.scopeType == ScopeType.PACKAGE -> {
                    return scopeI.typeWithoutArgs
                }
                scopeI.scopeType == ScopeType.METHOD -> {
                    val func = scopeI.selfAsMethod!!
                    val self = func.selfType
                    if (self != null) {
                        val selfScope = typeToScope(self)!!
                        LOGGER.info("Method-SelfType[${scopeI.pathStr}]: $self -> $selfScope")
                        return resolveThisScope(selfScope).typeWithoutArgs
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

            val selfMatch = scope.children.firstOrNull { it.name == name && it.isClassType() }
            if (selfMatch != null) {
                val typeParams: List<Type>? =
                    if (selfMatch.hasTypeParameters && selfMatch.typeParameters.isEmpty()) emptyList() else null
                return ClassType(selfMatch, typeParams)
            }

            val genericsMatch = scope.typeParameters.firstOrNull { it.name == name }
            if (genericsMatch != null) return GenericType(genericsMatch.scope, genericsMatch.name)

            scope = scope.parentIfSameFile ?: return null
        }
    }
}