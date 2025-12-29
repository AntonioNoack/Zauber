package me.anno.zauber.types

import me.anno.zauber.types.impl.*

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

    abstract fun toStringImpl(depth: Int): String
    override fun toString(): String = toStringImpl(10)
    fun toString(depth: Int): String {
        return if (depth >= 0) toStringImpl(depth - 1) else "${javaClass.simpleName}..."
    }

    open fun not(): Type = NotType(this)
}