package me.anno.zauber.types.impl

import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NothingType

class UnionType(val types: List<Type>) : Type() {

    companion object {
        /**
         * OR
         * */
        fun unionTypes(typeA: Type, typeB: Type): Type {
            if (typeA == typeB) return typeA
            if (typeA == NothingType) return typeB
            if (typeB == NothingType) return typeA
            return UnionType((getTypes(typeA) + getTypes(typeB)).distinct())
        }

        fun getTypes(type: Type): List<Type> {
            return if (type is UnionType) type.types else listOf(type)
        }
    }

    init {
        check(types.size >= 2)
    }

    override fun toString(depth: Int): String {
        val newDepth = depth - 1
        return "UnionType(${types.joinToString { it.toString(newDepth) }})"
    }

    override fun equals(other: Any?): Boolean {
        return other is UnionType &&
                types.toSet() == other.types.toSet()
    }

    override fun hashCode(): Int {
        return types.toSet().hashCode()
    }
}