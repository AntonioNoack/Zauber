package me.anno.zauber.types.impl

import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NothingType
import me.anno.zauber.types.Types.NullableAnyType
import me.anno.zauber.types.impl.TypeUtils.getHierarchyDepth
import me.anno.zauber.types.impl.TypeUtils.isChildTypeOf
import me.anno.zauber.types.impl.TypeUtils.isDistinctFrom
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes

class AndType(val types: List<Type>) : Type() {

    companion object {

        fun andTypes(typeA: Type, typeB: Type): Type {
            if (typeA == typeB) return typeA
            if (typeA is UnionType && typeB is NotType &&
                typeB.type in typeA.types
            ) {
                val filteredA = unionTypes(typeA.types.filter { it != typeB.type })
                return if (typeB.type == NullType) filteredA
                else andTypes(filteredA, typeB)
            }

            val joint = reduceAndTypes(getTypes(typeA) + getTypes(typeB))
            return when (joint.size) {
                0 -> NothingType
                1 -> joint.first()
                else -> AndType(joint)
            }
        }

        fun andTypes(types: List<Type>): Type {
            if (types.isEmpty()) return NothingType
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
            val yesTypes = types.filter { it !is NotType && it != NullableAnyType }
            for (i in 1 until yesTypes.size) {
                for (j in 0 until i) {
                    if (!canInstanceBeBoth(yesTypes[i], yesTypes[j])) {
                        // or return empty list?
                        return listOf(NothingType)
                    }
                }
            }

            if (yesTypes == types) {
                return types
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
            // todo unit-test that AndType(Exception,Throwable) = Exception
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

        private fun canInstanceBeBoth(t1: Type, t2: Type): Boolean {
            if (t1 == NothingType || t2 == NothingType) return false
            if (t1 == t2) return true
            if (t1 is UnionType) return t1.types.any { t1i -> canInstanceBeBoth(t1i, t2) }
            if (t2 is UnionType) return t2.types.any { t2i -> canInstanceBeBoth(t1, t2i) }
            if (t1 is NullType && t2 is ClassType) return false
            if (t2 is NullType && t1 is ClassType) return false
            if (t1 is ClassType && t2 is ClassType &&
                isDistinctFrom(t1, t2)
            ) return false

            // todo complete this... is complicated...
            //  and ideally, all these should be resolved/specialized...
            return true // idk
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