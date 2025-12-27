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
                val filteredA = typeA.types.filter { it != typeB.type }
                    .reduce { a, b -> unionTypes(a, b) }
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
        check(types.size >= 2)
    }

    override fun toString(depth: Int): String {
        val newDepth = depth - 1
        return "AndType(${types.joinToString { it.toString(newDepth) }})"
    }

    override fun equals(other: Any?): Boolean {
        return other is AndType &&
                types.toSet() == other.types.toSet()
    }

    override fun hashCode(): Int {
        return types.toSet().hashCode()
    }
}