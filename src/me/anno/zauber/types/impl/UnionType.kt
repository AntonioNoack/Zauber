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

        /**
         * OR
         * */
        fun unionTypes(types: List<Type>, typeB: Type): Type {
            if (types.isEmpty()) return typeB
            val uniqueTypes = (types + typeB).distinct()
            if (uniqueTypes.size == 1) return uniqueTypes[0]
            return UnionType(uniqueTypes)
        }

        /**
         * OR
         * */
        fun unionTypes(types: List<Type>): Type {
            if (types.isEmpty()) return NothingType
            val uniqueTypes = types.distinct()
            if (uniqueTypes.size == 1) return uniqueTypes[0]
            return UnionType(uniqueTypes)
        }

        fun getTypes(type: Type): List<Type> {
            return if (type is UnionType) type.types else listOf(type)
        }
    }

    init {
        check(types.size >= 2) {
            "Union type should have at least two types"
        }
    }

    override fun toStringImpl(depth: Int): String {
        return "UnionType(${types.joinToString { it.toString(depth) }})"
    }

    override fun equals(other: Any?): Boolean {
        return other is UnionType &&
                types.toSet() == other.types.toSet()
    }

    override fun hashCode(): Int {
        return types.toSet().hashCode()
    }
}