package me.anno.zauber.types.impl.arithmetic

import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.CollectionType
import me.anno.zauber.types.impl.TypeUtils.canInstanceBeBoth
import me.anno.zauber.types.impl.TypeUtils.getHierarchyDepth
import me.anno.zauber.types.impl.TypeUtils.isChildTypeOf
import me.anno.zauber.types.impl.arithmetic.UnionType.Companion.unionTypes
import me.anno.zauber.types.impl.unresolved.UnresolvedAndType
import me.anno.zauber.types.impl.unresolved.UnresolvedType

class AndType(types: List<Type>) : CollectionType(types) {

    companion object {

        fun andTypes(typeA: Type, typeB: Type): Type {
            if (typeA is UnresolvedType || typeB is UnresolvedType) {
                return UnresolvedAndType(listOf(typeA, typeB))
            }

            if (typeA == typeB) return typeA
            if (typeA == NullType || typeB == NullType ||
                typeA == Types.Nothing || typeB == Types.Nothing
            ) return Types.Nothing
            if (typeA == UnknownType) return typeB
            if (typeB == UnknownType) return typeA

            if (typeA is UnionType && typeB is NotType &&
                typeB.type in typeA.types
            ) {
                val filteredA = unionTypes(typeA.types.filter { it != typeB.type })
                return if (typeB.type == NullType) filteredA
                else andTypes(filteredA, typeB)
            }

            val joint = reduceAndTypes(getTypes(typeA) + getTypes(typeB))
            return when (joint.size) {
                0 -> Types.Nothing
                1 -> joint.first()
                else -> AndType(joint)
            }
        }

        fun andTypes(types: List<Type>): Type {
            if (types.isEmpty()) return Types.Nothing
            if (types.any { it is UnresolvedType }) {
                return UnresolvedAndType(types)
            }

            val uniqueTypes = reduceAndTypes(types)
            if (uniqueTypes.size == 1) return uniqueTypes[0]
            return AndType(uniqueTypes)
        }

        fun getTypes(type: Type): List<Type> {
            return if (type is AndType) type.types else listOf(type)
        }

        private fun reduceAndTypes(types: List<Type>): List<Type> {
            val types = types.distinct()
            val notTypes = types.filterIsInstance<NotType>()
            val yesTypes = types.filter { it !is NotType && it != Types.NullableAny }

            for (i in 1 until yesTypes.size) {
                for (j in 0 until i) {
                    if (!canInstanceBeBoth(yesTypes[i], yesTypes[j])) {
                        // or return empty list?
                        return listOf(Types.Nothing)
                    }
                }
            }

            if (yesTypes == types) {
                return reduceAndTypes2(types)
            }

            val notTypesOr = notTypes.flatMap {
                UnionType.getTypes(it.type)
            }.distinct().filter { notType ->
                yesTypes.any { yesType -> canInstanceBeBoth(yesType, notType) }
            }

            if (notTypesOr.isEmpty()) {
                return yesTypes
            }

            return reduceAndTypes2(yesTypes) + NotType(unionTypes(notTypesOr))
        }

        private fun reduceAndTypes2(types: List<Type>): List<Type> {
            var classTypes = types.filterIsInstance<ClassType>()

            // remove parents: if a child is included, remove the parent
            if (classTypes.size > 1) {
                classTypes = classTypes.sortedBy { it.clazz.getHierarchyDepth() }
                classTypes = classTypes.filterIndexed { index, parentType ->
                    classTypes.subList(index + 1, classTypes.size).none { childType ->
                        childType.isChildTypeOf(parentType)
                    }
                }
            }

            val nonClassTypes = types.filter { it !is ClassType }
            return classTypes + nonClassTypes
        }
    }

    init {
        check(types.size >= 2) {
            "AndType should have at least two types inside"
        }
    }

    override fun withTypes(types: List<Type>): Type {
        return andTypes(types)
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