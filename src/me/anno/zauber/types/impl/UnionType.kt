package me.anno.zauber.types.impl

import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NothingType
import me.anno.zauber.types.impl.TypeUtils.getHierarchyDepth
import me.anno.zauber.types.impl.TypeUtils.isChildTypeOf

class UnionType(val types: List<Type>) : Type() {

    companion object {

        // todo just like with And-types, we want some simplification!
        //  e.g. Int|Number -> Number, because Int extends Number

        fun unionTypes(typeA: Type, typeB: Type): Type {
            if (typeA == typeB) return typeA
            if (typeA == NothingType) return typeB
            if (typeB == NothingType) return typeA
            return reduceUnionTypes(getTypes(typeA) + getTypes(typeB))
        }

        fun unionTypes(types: List<Type>, typeB: Type): Type {
            if (types.isEmpty()) return typeB
            return unionTypes(types + typeB)
        }

        fun unionTypes(types: List<Type>): Type {
            if (types.isEmpty()) return NothingType
            return reduceUnionTypes(types.flatMap { typeI -> getTypes(typeI) })
        }

        fun getTypes(type: Type): List<Type> {
            return if (type is UnionType) type.types else listOf(type)
        }

        fun reduceUnionTypes(types: List<Type>): Type {
            val types = types.distinct().filter { it != NothingType }
            if (types.isEmpty()) return NothingType
            if (types.size == 1) return types[0]

            // todo we can do something equivalent with andTypes
            // sort entries by depth,
            //  and remove any that are children of others...
            var classTypes = types.filterIsInstance<ClassType>()
            if (classTypes.size > 1) {
                classTypes = classTypes.sortedBy { it.clazz.getHierarchyDepth() }
                classTypes = classTypes.filterIndexed { index, childType ->
                    classTypes.subList(0, index).none { parentType ->
                        childType.isChildTypeOf(parentType)
                    }
                }
            }

            val nonClassTypes = types.filter { it !is ClassType }
            val jointTypes = classTypes + nonClassTypes
            if (jointTypes.isEmpty()) return NothingType
            if (jointTypes.size == 1) return jointTypes[0]
            return UnionType(jointTypes)
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