package me.anno.zauber.types

import me.anno.generation.Specializations
import me.anno.utils.ResetThreadLocal
import me.anno.zauber.Compile
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.SuperCall
import me.anno.zauber.interpreting.ZClass
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType
import me.anno.zauber.types.impl.arithmetic.NullType
import me.anno.zauber.types.impl.arithmetic.UnknownType

// todo we want to define what a specialization actually can contain,
//  e.g. it makes no sense to create 100 variants for wrapper-of-pointer for ArrayList.
//  useful: native types, value types, 'else'
// todo what about Int? (could be optimized after all)
// todo Type-or-null could be mapped to value class Nullable(val value: V, val isNull: Boolean)

class Specialization(val scope: Scope?, typeParameters: ParameterList) {

    @Deprecated("This is incomplete for inner classes, where the outer class is generic")
    constructor(classType: ClassType) : this(
        classType.clazz, ParameterList(
            classType.clazz.typeParameters,
            classType.typeParameters ?: emptyList()
        )
    )

    inline fun <R> use(runnable: () -> R): R {
        return try {
            Specializations.specializations.add(this)
            runnable()
        } finally {
            @Suppress("Since15")
            Specializations.specializations.removeLast()
        }
    }

    val typeParameters = typeParameters.readonly()
    val hash = typeParameters.hashCode() and 0x7fff_ffff

    init {
        validateCompleteness()
    }

    /**
     * check that the specialization contains exactly what we require
     * */
    fun validateCompleteness() {
        if (scope == null) return

        val actualGenerics = typeParameters.generics
        val expectedGenerics = collectGenerics(scope)
        val matchesGenerics = actualGenerics.toSet() == expectedGenerics.toSet()
        if (!matchesGenerics) {
            throw IllegalStateException("Mismatched generics for $scope: got $typeParameters, expected $expectedGenerics")
        }
    }

    fun isEmpty(): Boolean = typeParameters.isEmpty()
    fun isNotEmpty(): Boolean = typeParameters.isNotEmpty()

    fun containsGenerics(): Boolean {
        return typeParameters.any { it is GenericType }
    }

    override fun equals(other: Any?): Boolean {
        return other is Specialization &&
                scope == other.scope &&
                typeParameters == other.typeParameters
    }

    operator fun get(type: GenericType): Type? {
        val index = typeParameters.generics.indexOfFirst { it.name == type.name && it.scope == type.scope }
        if (index < 0) return null
        val resolved = typeParameters.getOrNull(index)
            ?: typeParameters.generics[index].type
        return if (type != resolved) resolved else null
    }

    operator fun get(type: Parameter): Type? {
        return get(GenericType(type.scope, type.name))
    }

    operator fun plus(other: Specialization): Specialization {
        if (scope == null) return other
        if (other.scope == null) return this
        if (scope == other.scope) return other
        if (scope.isInsideOf(other.scope)) return this
        if (other.scope.isInsideOf(scope)) return other
        return Specialization(null, typeParameters + other.typeParameters)
    }

    fun indexOf(type: Type): Int {
        if (type !is GenericType) return -1
        val index = typeParameters.generics.indexOfFirst { it.name == type.name && it.scope == type.scope }
        if (index < 0) return -1
        val resolved = typeParameters.getOrNull(index)
            ?: typeParameters.generics[index].type
        return if (type != resolved) index else -1
    }

    operator fun contains(type: Type): Boolean {
        return indexOf(type) >= 0
    }

    override fun hashCode(): Int = hash

    fun createUniqueName(): String {
        val name = data.uniqueNames[this]
        if (name != null) return name

        val genName0 = typeParameters.indices.joinToString("_") {
            when (val type = typeParameters.getOrNull(it)) {
                is GenericType -> {
                    val selfAsMethod = type.scope.selfAsMethod
                    if (selfAsMethod != null) {
                        "${selfAsMethod.name}_${type.name}"
                    } else {
                        "${type.scope.name}_${type.name}"
                    }
                }
                NullType -> "null"
                null, UnknownType -> "?"
                // todo prefer a short name, so don't use full paths...
                else -> type.toString()
            }
                .replace("(ro)", "")
                .replace(".", "")
                .replace(":", "")
                .replace('<', 'X')
                .replace('>', 'x')
                .replace('(', 'X')
                .replace(')', 'x')
                .replace('[', 'X')
                .replace(']', 'x')
                .replace(", ", "_")
                .replace(",", "_")
                .replace("?", "$")
        }

        if (data.knownNames.add(genName0)) {
            data.uniqueNames[this] = genName0
            return genName0
        }

        for (i in 0 until 1000) {
            val genNameI = "$genName0$i"
            if (data.knownNames.add(genNameI)) {
                data.uniqueNames[this] = genNameI
                return genNameI
            }
        }
        throw IllegalStateException("Too many duplicates of $genName0")
    }

    override fun toString(): String {
        return List(typeParameters.generics.size) { index ->
            IndexedValue(index, typeParameters.generics[index].scope)
        }
            .groupBy { it.value }.entries
            .joinToString(", ", "{", "}") { (key, value) ->
                val indices = value.map { it.index }
                "${key.pathStr}: ${
                    indices.map { index ->
                        val name = typeParameters.generics[index].name
                        val type = typeParameters.getOrNull(index)
                        "$name=$type"
                    }
                }"
            }
    }

    fun withScope(scope: Scope): Specialization {
        return if (this.scope == scope) this
        else Specialization(scope, typeParameters)
    }

    val superType: Specialization?
        get() {

            val clazz = scope!!
            check(clazz.isClassLike())

            if (clazz.isPackage()) {
                return fromSimple(Types.Any.clazz)
            }

            val superCall = clazz[ScopeInitType.AFTER_DISCOVERY]
                .superCalls.firstOrNull { superCall -> superCall.isClassCall }
                ?: return null

            return getSuperType(superCall)
        }

    fun getSuperType(superCall: SuperCall): Specialization {

        val clazz = scope!!
        check(clazz.isClassLike())

        if (clazz.isPackage()) {
            return fromSimple(Types.Any.clazz)
        }

        val superScope = superCall.type.clazz

        // todo we must also check const value-params
        val generics = superScope.typeParameters
        if (generics.isEmpty() && !superScope.isInnerClass()) {
            return fromSimple(superScope)
        }

        // todo we must also check const value-params
        val typeParams = superCall.type.typeParameters ?: emptyList()
        val superTypeParams = typeParams.map { type -> type.specialize(this) }
        return Specialization(superScope, ParameterList(generics, superTypeParams))
    }

    val clazz: Scope
        get() {
            check(scope != null)
            check(scope.isClassLike()) {
                "$scope is not class-like: ${scope.scopeType}"
            }
            return scope
        }

    val method: MethodLike
        get() {
            check(scope != null)
            check(scope.isMethodLike())
            return scope.selfAsMethod
                ?: scope.selfAsConstructor
                ?: throw IllegalStateException("$scope[${scope.scopeType}] is method-like, but has no method?")
        }

    val field: Field
        get() {
            check(scope != null)
            check(scope.scopeType == ScopeType.FIELD)
            return scope.selfAsField!!
        }

    fun isClassLike() = scope != null && scope.isClassLike()
    fun isMethodLike() = scope != null && scope.isMethodLike()

    companion object {

        class Data {
            val uniqueNames = HashMap<Specialization, String>()
            val knownNames = HashSet<String>()
        }

        private val data by ResetThreadLocal.threadLocal { Data() }
        private val cache by ResetThreadLocal.threadLocal { HashMap<Scope, Specialization>() }

        fun fromSimple(scope: Scope): Specialization {
            check(scope.typeParameters.isEmpty())
            check(scope.isClassLike() || scope.isMethodLike())
            return cache.getOrPut(scope) {
                Specialization(scope, ParameterList.emptyParameterList())
            }
        }

        fun collectGenerics(scope: Scope): List<Parameter> {
            var scope = scope
            val result = ArrayList<Parameter>()
            while (true) {
                result.addAll(scope.typeParameters)

                if (scope.isClass()) {
                    val constr = scope.getOrCreatePrimaryConstructorScope()
                        .selfAsConstructor!!
                    for (param in constr.valueParameters) {
                        if (param.isConst) {
                            result.add(param)
                        }
                    }
                }

                if (scope.isObjectLike()) break
                if (scope.isClass() &&
                    scope.scopeType != ScopeType.INNER_CLASS &&
                    scope.scopeType != ScopeType.INLINE_CLASS
                ) break

                // todo only accept parent-parameters on some conditions
                scope = scope.parentIfSameFile ?: break
            }
            return result
        }

        fun filterSpecialization(type: Type, generic: Parameter): Type {
            if (generic.isVal) return type
            return when (type) {
                in ZClass.nativeTypes -> type
                is ClassType if type.clazz.isValueType() -> type
                else -> generic.type
            }
        }

        val noSpecialization by ResetThreadLocal.threadLocal {
            Specialization(Compile.root, ParameterList.emptyParameterList())
        }
    }
}