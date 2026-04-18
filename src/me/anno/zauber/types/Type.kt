package me.anno.zauber.types

import me.anno.zauber.ast.rich.TypeOfField
import me.anno.zauber.ast.rich.ZauberASTBuilderBase.Companion.resolveTypeByName
import me.anno.generation.Specializations.specialization
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ParameterList.Companion.resolveGenerics
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.impl.*
import me.anno.zauber.types.impl.AndType.Companion.andTypes
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes
import me.anno.zauber.types.specialization.Specialization

abstract class Type {

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
            is TypeOfField, is UnresolvedType -> false
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
            else -> throw NotImplementedError("Resolve type ${javaClass.simpleName}, $this")
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