package me.anno.zauber.types

import me.anno.zauber.ast.rich.ZauberASTBuilderBase.Companion.resolveTypeByName
import me.anno.zauber.types.impl.*
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes

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
            NullType -> true
            is GenericType,
            is LambdaType,
            is UnresolvedType,
            is SelfType,
            is ThisType -> false
            is ClassType -> !clazz.isTypeAlias() && (typeParameters?.all { it.isResolved() } ?: true)
            is UnionType -> types.all { it.isResolved() }
            else -> throw NotImplementedError("Is $this (${javaClass.simpleName}) resolved?")
        }
    }

    fun resolve(): Type {
        if (isResolved()) return this
        return when (this) {
            is GenericType,
            is LambdaType,
            is SelfType,
            is ThisType -> throw IllegalStateException()
            is ClassType -> throw NotImplementedError("Resolve $this")
            // is ClassType -> !clazz.isTypeAlias() && (typeParameters?.all { it.containsGenerics() } ?: true)
            is UnionType -> unionTypes(types.map { it.resolve() })
            is UnresolvedType -> {
                resolveTypeByName(null, className, scope, imports)
                    ?: throw IllegalStateException("Could not resolve $this")
            }
            else -> throw NotImplementedError("Resolve $this")
        }
    }

    abstract fun toStringImpl(depth: Int): String
    override fun toString(): String = toStringImpl(10)
    fun toString(depth: Int): String {
        return if (depth >= 0) toStringImpl(depth - 1) else "${javaClass.simpleName}..."
    }

    open fun not(): Type = NotType(this)
}