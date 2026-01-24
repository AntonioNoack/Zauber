package me.anno.zauber.types

import me.anno.zauber.ast.rich.TypeOfField
import me.anno.zauber.ast.rich.ZauberASTBuilderBase.Companion.resolveTypeByName
import me.anno.zauber.generation.Specializations
import me.anno.zauber.generation.Specializations.specialization
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Types.NothingType
import me.anno.zauber.types.impl.*
import me.anno.zauber.types.impl.AndType.Companion.andTypes
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes
import me.anno.zauber.types.specialization.Specialization

abstract class Type {

    fun containsGenerics(): Boolean {
        return when (this) {
            NullType -> false
            is ClassType -> typeParameters?.any { it.containsGenerics() } ?: false
            is UnionType -> types.any { it.containsGenerics() }
            is GenericType -> true
            is LambdaType -> parameters.any { it.type.containsGenerics() } || returnType.containsGenerics()
            else -> throw NotImplementedError("Does $this contain generics?")
        }
    }

    fun isResolved(): Boolean {
        return when (this) {
            NullType, UnknownType -> true
            is GenericType -> !specialization.contains(this)
            is TypeOfField,
            is LambdaType,
            is UnresolvedType,
            is SelfType,
            is ThisType -> false
            is ClassType -> !clazz.isTypeAlias() &&
                    (typeParameters == null || typeParameters.indices.all {
                        typeParameters.getOrNull(it)?.isResolved() != false
                    })
            is UnionType -> types.all { it.isResolved() }
            is ComptimeValue -> true // is it though?
            else -> throw NotImplementedError("Is $this (${javaClass.simpleName}) resolved?")
        }
    }

    fun resolve(): Type {
        if (isResolved()) return this
        val resolved = resolveImpl()
        if (resolved == this) throw IllegalStateException("Failed to resolve $this (${javaClass.simpleName})")
        return resolved.resolve()
    }

    fun resolveImpl(): Type {
        return when (this) {
            is LambdaType,
            is SelfType,
            is ThisType -> throw IllegalStateException("Cannot resolve $this without scope data")
            is GenericType -> specialization[this]!!
            is ClassType -> {
                val parameters = typeParameters?.map { it.resolve() }
                ClassType(clazz, parameters)
            }
            is TypeOfField -> {
                val context = ResolutionContext(null, false, null, emptyMap())
                field.resolveValueType(context)
            }
            // is ClassType -> !clazz.isTypeAlias() && (typeParameters?.all { it.containsGenerics() } ?: true)
            is UnionType -> unionTypes(types.map { it.resolve() })
            is UnresolvedType -> {
                resolveTypeByName(null, className, scope, imports)
                    ?: throw IllegalStateException("Could not resolve $this")
            }
            else -> throw NotImplementedError("Resolve type ${javaClass.simpleName}, $this")
        }
    }

    fun isFullySpecialized(): Boolean {
        return when (this) {
            NullType, NothingType,
            is UnknownType -> true
            is GenericType -> false
            is ClassType -> typeParameters == null || typeParameters.all { it.isFullySpecialized() }
            is UnionType -> types.all { it.isFullySpecialized() }
            is AndType -> types.all { it.isFullySpecialized() }
            is NotType -> type.isFullySpecialized()
            else -> throw IllegalStateException("Is ${javaClass.simpleName} fully specialized?")
        }
    }

    fun specialize(spec: Specialization = specialization): Type {
        if (isFullySpecialized()) return this
        return when (this) {
            is ClassType -> ClassType(clazz, typeParameters!!.map { it.specialize(spec) })
            is UnionType -> unionTypes(types.map { it.specialize(spec) })
            is AndType -> andTypes(types.map { it.specialize(spec) })
            is GenericType -> spec[this] ?: this
            is NotType -> type.specialize(spec).not()
            else -> throw IllegalStateException("Specialize ${javaClass.simpleName}")
        }
    }

    abstract fun toStringImpl(depth: Int): String
    override fun toString(): String = toStringImpl(10)

    fun toString(depth: Int): String {
        return if (depth >= 0) toStringImpl(depth - 1) else "${javaClass.simpleName}..."
    }

    open fun not(): Type = NotType(this)
}