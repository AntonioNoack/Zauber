package me.anno.zauber.types

import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType
import me.anno.zauber.types.impl.LambdaType
import me.anno.zauber.types.impl.NotType
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.impl.UnionType

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

    abstract fun toString(depth: Int): String
    override fun toString(): String = toString(10)

    open fun not(): Type = NotType(this)
}