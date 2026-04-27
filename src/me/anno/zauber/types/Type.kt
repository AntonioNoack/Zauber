package me.anno.zauber.types

import me.anno.generation.Specializations.specialization
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.TypeOfField
import me.anno.zauber.ast.rich.ZauberASTBuilderBase.Companion.resolveTypeByName
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ParameterList.Companion.resolveGenerics
import me.anno.zauber.typeresolution.ParameterList.Companion.resolveGenericsOrNull
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.impl.*
import me.anno.zauber.types.impl.AndType.Companion.andTypes
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes
import me.anno.zauber.types.impl.unresolved.UnresolvedAndType
import me.anno.zauber.types.impl.unresolved.UnresolvedNotType
import me.anno.zauber.types.impl.unresolved.UnresolvedType
import me.anno.zauber.types.impl.unresolved.UnresolvedUnionType
import me.anno.zauber.types.specialization.Specialization

abstract class Type {

    companion object {
        private val LOGGER = LogManager.getLogger(Type::class)
    }

    fun contains(type: GenericType): Boolean {
        if (this == type) return true
        return when (this) {
            is UnionType -> types.any { member -> member.contains(type) }
            is AndType -> types.any { member -> member.contains(type) }
            NullType -> false
            is SelfType, is ThisType -> throw NotImplementedError("Does $this contain $type?")
            is ClassType -> typeParameters?.any { it.contains(type) } == true
            is LambdaType -> (selfType?.contains(type) ?: false) || returnType.contains(type)
            is GenericType -> false // not the same; todo we might need to check super/redirects
            is UnresolvedType -> resolvedName.contains(type)
            else -> throw NotImplementedError("Does ${this.javaClass.simpleName} contain $type?")
        }
    }

    fun containsGenerics(): Boolean {
        return when (this) {
            NullType -> false
            is ClassType -> typeParameters?.any { it.containsGenerics() } ?: false
            is UnionType -> types.any { it.containsGenerics() }
            is AndType -> types.any { it.containsGenerics() }
            is NotType -> type.containsGenerics()
            is GenericType -> true
            is LambdaType -> parameters.any { it.type.containsGenerics() } || returnType.containsGenerics()
            is UnresolvedType -> resolvedName.containsGenerics()
            else -> throw NotImplementedError("Does $this contain generics?")
        }
    }

    fun isResolved(): Boolean {
        return when (this) {
            NullType, UnknownType, is SelfType, is ThisType -> true
            is GenericType -> !specialization.contains(this)
            is TypeOfField,
            is UnresolvedType, is UnresolvedUnionType,
            is UnresolvedNotType, is UnresolvedAndType -> false
            is LambdaType -> selfType?.isResolved() != false &&
                    parameters.all { it.type.isResolved() } &&
                    returnType.isResolved()
            is ClassType -> !clazz.isTypeAlias() &&
                    (typeParameters == null || typeParameters.indices.all {
                        typeParameters.getOrNull(it)?.isResolved() != false
                    })
            is UnionType -> types.all { it.isResolved() }
            is AndType -> types.any { it.isResolved() }
            is NotType -> type.isResolved()
            is ComptimeValue -> true // is it though?
            else -> throw NotImplementedError("Is $this (${javaClass.simpleName}) resolved?")
        }
    }

    fun removeNull(): Type {
        return when (this) {
            is ClassType -> this
            is UnresolvedType -> resolvedName.removeNull()
            is NullType -> Types.Nothing
            is UnionType -> {
                if (types.any { it == NullType }) unionTypes(types - NullType)
                else this
            }
            is AndType -> {
                if (types.any { it is NotType && it.type == NullType }) this
                else andTypes(this, NotType(NullType))
            }
            else -> andTypes(this, NotType(NullType))
        }
    }

    fun resolve(selfScope: Scope? = null): Type {
        var self = this
        repeat(100) {
            if (self.isResolved()) return self
            val resolved = self.resolveImpl(selfScope)
            if (resolved == self) throw IllegalStateException("Failed to resolve $this (${javaClass.simpleName}), returned self")
            self = resolved
        }
        throw IllegalStateException("Failed to resolve $self, too complicated, maybe a recursive type?")
    }

    fun resolveImpl(selfScope: Scope?): Type {
        return when (this) {
            is LambdaType -> {
                LambdaType(selfType?.resolve(selfScope), parameters.map {
                    LambdaParameter(it.name, it.type.resolve(selfScope))
                }, returnType.resolve(selfScope))
            }
            is GenericType -> specialization[this]!!
            is ClassType -> {
                if (clazz.isTypeAlias()) {
                    val typeAlias = clazz.selfAsTypeAlias!!
                    val genericNames = clazz.typeParameters
                    if (genericNames.isEmpty()) {
                        check((typeParameters?.size ?: 0) == 0)
                        typeAlias
                    } else {
                        val genericValues = ParameterList(genericNames, typeParameters ?: emptyList())
                        genericValues.resolveGenerics(null, typeAlias)
                    }
                } else {
                    val parameters = typeParameters!!.map { it.resolve(selfScope) }
                    ClassType(clazz, parameters)
                }
            }
            is TypeOfField -> {
                val context = ResolutionContext(null, false, null, emptyMap())
                field.resolveValueType(context)
            }
            // is ClassType -> !clazz.isTypeAlias() && (typeParameters?.all { it.containsGenerics() } ?: true)
            is UnionType -> unionTypes(types.map { it.resolve(selfScope) })
            is AndType -> andTypes(types.map { it.resolve(selfScope) })
            is NotType -> type.resolve(selfScope).not()
            is UnresolvedType -> {
                val baseType = resolveTypeByName(findSelfType(scope), className, scope, imports)
                    ?: throw IllegalStateException("Could not resolve $this in '$scope'")
                if (!typeParameters.isNullOrEmpty()) {
                    baseType as ClassType
                    check(
                        baseType.typeParameters == null ||
                                baseType.typeParameters.all { it is GenericType && it.scope == baseType.clazz }) {
                        "Expected $baseType to not have type parameters, because we have $typeParameters"
                    }
                    baseType.withTypeParameters(typeParameters)
                } else baseType
            }
            is UnresolvedNotType -> type.resolve(selfScope).not()
            is UnresolvedUnionType -> unionTypes(types.map { it.resolve(selfScope) })
            is UnresolvedAndType -> andTypes(types.map { it.resolve(selfScope) })
            else -> throw NotImplementedError("Resolve type ${javaClass.simpleName}, $this")
        }
    }

    fun resolveGenerics(
        selfType: Type?,
        genericNames: List<Parameter>,
        genericValues: ParameterList?
    ): Type {
        if (genericValues == null) return this
        return when (val type = this) {
            is GenericType -> {
                check(genericNames.size == genericValues.size) {
                    "Expected same number of generic names and generic values, got $genericNames vs $genericValues ($type)"
                }
                val idx = genericNames.indexOfFirst { it.name == type.name && it.scope == type.scope }
                genericValues.getOrNull(idx) ?: type
            }
            is UnionType -> {
                val types = types
                    .map { genericValues.resolveGenerics(selfType, it) }
                unionTypes(types)
            }
            is AndType -> {
                val types = types
                    .map { genericValues.resolveGenerics(selfType, it) }
                andTypes(types)
            }
            is ClassType -> {
                val typeArgs = typeParameters
                if (typeArgs.isNullOrEmpty()) return type
                val newTypeArgs = typeArgs.map { genericValues.resolveGenerics(selfType, it) }
                if (false && typeArgs != newTypeArgs) {
                    LOGGER.info("Mapped types: $typeArgs -> $newTypeArgs")
                }
                ClassType(type.clazz, newTypeArgs)
            }
            NullType, UnknownType -> type
            is LambdaType -> {
                val newSelfType = genericValues.resolveGenericsOrNull(selfType, type.selfType)
                val newReturnType = genericValues.resolveGenerics(selfType, type.returnType)
                val newParameters = type.parameters.map {
                    val newType = genericValues.resolveGenerics(selfType, it.type)
                    LambdaParameter(it.name, newType)
                }
                LambdaType(newSelfType, newParameters, newReturnType)
            }
            is SelfType -> selfType ?: genericValues.resolveGenerics(selfType, type.scope.typeWithArgs)
            is ThisType -> selfType ?: genericValues.resolveGenerics(selfType, type.type)
            is TypeOfField -> {
                val valueType = type.field.valueType
                if (valueType != null) {
                    valueType.resolveGenerics(selfType, genericNames, genericValues)
                } else {
                    val context = ResolutionContext(type.field.selfType, false, null, emptyMap())
                    type.field.resolveValueType(context)
                }
            }
            is NotType -> type.type.resolveGenerics(selfType, genericNames, genericValues).not()
            is UnresolvedType -> type.resolvedName.resolveGenerics(selfType, genericNames, genericValues)
            is UnresolvedNotType -> type.type.resolveGenerics(selfType, genericNames, genericValues).not()
            is UnresolvedUnionType ->
                unionTypes(types.map { it.resolveGenerics(selfType, genericNames, genericValues) })
            is UnresolvedAndType ->
                andTypes(types.map { it.resolveGenerics(selfType, genericNames, genericValues) })
            else -> throw NotImplementedError("Resolve generics in $type (${type.javaClass.simpleName})")
        }
    }

    fun replace(oldType: ClassType, newType: ClassType): Type {
        return when (this) {
            oldType -> newType
            is UnionType -> unionTypes(types.map { it.replace(oldType, newType) })
            is AndType -> andTypes(types.map { it.replace(oldType, newType) })
            is NotType -> type.replace(oldType, newType).not()
            is GenericType, NullType -> this
            is UnresolvedType -> {
                if (typeParameters.isNullOrEmpty()) this
                else UnresolvedType(
                    className, typeParameters.map { it.replace(oldType, newType) },
                    scope, imports
                )
            }
            is ClassType -> {
                if (typeParameters.isNullOrEmpty()) this
                else ClassType(clazz, typeParameters.map { it.replace(oldType, newType) })
            }
            else -> throw NotImplementedError("Replace type ${javaClass.simpleName}, $this")
        }
    }

    private fun findSelfType(scope: Scope): Type? {
        var scope = scope
        while (true) {
            if (scope.isClassLike()) {
                return scope.typeWithArgs
            }

            scope = scope.parentIfSameFile
                ?: return null
        }
    }

    fun isFullySpecialized(): Boolean {
        return when (this) {
            NullType, Types.Nothing,
            is UnknownType -> true
            is GenericType, is ThisType, is SelfType -> false
            is ClassType -> typeParameters?.all { member -> member.isFullySpecialized() } ?: true
            is UnionType -> types.all { member -> member.isFullySpecialized() }
            is AndType -> types.all { member -> member.isFullySpecialized() }
            is NotType -> type.isFullySpecialized()
            is LambdaType -> (selfType?.isFullySpecialized() ?: true) &&
                    parameters.all { it.type.isFullySpecialized() } &&
                    returnType.isFullySpecialized()
            is UnresolvedType -> false
            else -> throw IllegalStateException("Is ${javaClass.simpleName} fully specialized?")
        }
    }

    fun specialize(context: ResolutionContext): Type = specialize(context.specialization)

    fun specialize(spec: Specialization = specialization): Type {
        if (isFullySpecialized()) return this
        return when (this) {
            is ClassType -> ClassType(clazz, typeParameters!!.map { it.specialize(spec) })
            is UnionType -> unionTypes(types.map { it.specialize(spec) })
            is AndType -> andTypes(types.map { it.specialize(spec) })
            is GenericType -> spec[this] ?: this
            is NotType -> type.specialize(spec).not()
            is LambdaType -> {
                LambdaType(selfType?.specialize(spec), parameters.map {
                    LambdaParameter(it.name, it.type.specialize(spec))
                }, returnType.specialize(spec))
            }
            is UnresolvedType -> resolvedName.specialize(spec)
            // todo we need selfType to properly resolve them...
            is ThisType -> type.specialize(spec)
            is SelfType -> scope.typeWithArgs.specialize(spec)
            else -> throw IllegalStateException("Specialize ${javaClass.simpleName}")
        }
    }

    abstract fun toStringImpl(depth: Int): String
    override fun toString(): String = toStringImpl(10)

    fun toString(depth: Int): String {
        return if (depth >= 0) toStringImpl(depth - 1) else "${javaClass.simpleName}..."
    }

    open fun not(): Type = NotType(this)

    open val resolvedName: Type get() = this
}