package me.anno.zauber.types.impl

import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes

class AndType(val types: List<Type>) : Type() {

    companion object {
        /**
         * OR
         * */
        fun andTypes(typeA: Type, typeB: Type): Type {
            if (typeA == typeB) return typeA
            if (typeA is UnionType && typeB is NotType &&
                typeB.type in typeA.types
            ) {
                val filteredA = unionTypes(typeA.types.filter { it != typeB.type })
                return if (typeB.type == NullType) filteredA
                else andTypes(filteredA, typeB)
            }

            val joint = (getTypes(typeA) + getTypes(typeB)).distinct()
            if (joint.size == 1) return joint.first()
            return AndType(joint)
        }

        fun getTypes(type: Type): List<Type> {
            return if (type is AndType) type.types else listOf(type)
        }
    }

    init {
        check(types.size >= 2) {
            "AndType should have at least two types inside"
        }
    }

    override fun toStringImpl(depth: Int): String {
        return "AndType(${types.joinToString { it.toString(depth) }})"
    }

    override fun equals(other: Any?): Boolean {
        return other is AndType &&
                types.toSet() == other.types.toSet()
    }

    override fun hashCode(): Int {
        return types.toSet().hashCode()
    }
}