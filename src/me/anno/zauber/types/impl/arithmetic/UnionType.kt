package me.anno.zauber.types.impl.arithmetic

import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.CollectionType
import me.anno.zauber.types.impl.TypeUtils.getHierarchyDepth
import me.anno.zauber.types.impl.TypeUtils.isChildTypeOf
import me.anno.zauber.types.impl.unresolved.UnresolvedType
import me.anno.zauber.types.impl.unresolved.UnresolvedUnionType

class UnionType(types: List<Type>) : CollectionType(types) {

    companion object {

        // todo just like with And-types, we want some simplification!
        //  e.g. Int|Number -> Number, because Int extends Number

        fun unionTypes(typeA: Type, typeB: Type): Type {
            if (typeA is UnresolvedType || typeB is UnresolvedType) {
                return UnresolvedUnionType(listOf(typeA, typeB))
            }

            if (typeA == typeB) return typeA
            if (typeA == Types.Nothing) return typeB
            if (typeB == Types.Nothing) return typeA
            if (typeA == UnknownType || typeB == UnknownType) return UnknownType
            return reduceUnionTypes(getTypes(typeA) + getTypes(typeB))
        }

        fun unionTypes(types: List<Type>, typeB: Type): Type {
            if (types.isEmpty()) return typeB
            return unionTypes(types + typeB)
        }

        fun unionTypes(types: List<Type>): Type {
            if (types.isEmpty()) return Types.Nothing
            if (types.any { it is UnresolvedType }) {
                return UnresolvedUnionType(types)
            }
            return reduceUnionTypes(types.flatMap { typeI -> getTypes(typeI) })
        }

        fun getTypes(type: Type): List<Type> {
            return if (type is UnionType) type.types else listOf(type)
        }

        private fun reduceUnionTypes(types: List<Type>): Type {
            val types = types.distinct().filter { it != Types.Nothing }
            if (types.isEmpty()) return Types.Nothing
            if (types.size == 1) return types[0]
            if (UnknownType in types) return UnknownType

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
            if (jointTypes.isEmpty()) return Types.Nothing
            if (jointTypes.size == 1) return jointTypes[0]
            return UnionType(jointTypes)
        }
    }

    init {
        check(types.size >= 2) {
            "Union type should have at least two types"
        }
    }

    override fun withTypes(types: List<Type>): Type {
        return unionTypes(types)
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

    // todo we need another check & resolver for this...
    override val resolvedName: Type
        get() = unionTypes(types.map { it.resolvedName })
}