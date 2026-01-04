package me.anno.zauber.typeresolution

import me.anno.zauber.astbuilder.Parameter
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes

class ParameterList(val generics: List<Parameter>) : List<Type> {

    companion object {
        private val empty = ParameterList(emptyList())
        fun emptyParameterList(): ParameterList = empty
    }

    override val size: Int get() = generics.size

    constructor(generics: List<Parameter>, types: List<Type>) : this(generics) {
        check(generics.size == types.size)
        for (i in types.indices) {
            set(i, types[i], InsertMode.READ_ONLY)
        }
    }

    val types = arrayOfNulls<Type>(size)

    /**
     * return types are weak indicators, while parameters are strong indicators;
     * weak indicators are completely overridden by strong indicators;
     * when the strength is the same, the types need to be union-ed.
     * */
    val insertModes = Array(size) { InsertMode.WEAK }

    fun union(index: Int, newType: Type, newInsertMode: InsertMode): Boolean {
        val oldInsertMode = insertModes[index]
        return if (oldInsertMode == newInsertMode) {
            val oldType = types[index]
            types[index] = if (oldType != null) unionTypes(newType, oldType) else newType
            true
        } else if (newInsertMode.ordinal > oldInsertMode.ordinal) {
            types[index] = newType
            insertModes[index] = newInsertMode
            true
        } else false// else ignored
    }

    fun set(index: Int, newType: Type?, insertMode: InsertMode) {
        insertModes[index] = insertMode
        types[index] = newType
    }

    override fun isEmpty(): Boolean = size == 0
    override fun contains(element: Type): Boolean = element in types
    override fun iterator(): Iterator<Type> = listIterator(0)
    override fun containsAll(elements: Collection<Type>): Boolean = types.toList().containsAll(elements)

    override fun get(index: Int): Type = types[index]!!
    override fun indexOf(element: Type): Int = types.indexOf(element)
    override fun lastIndexOf(element: Type): Int = types.lastIndexOf(element)
    override fun listIterator(): ListIterator<Type> = listIterator(0)
    override fun listIterator(index: Int): ListIterator<Type> = types.map { it!! }.listIterator(index)

    fun map(mapping: (Type) -> Type): ParameterList {
        val dst = ParameterList(generics)
        for (i in generics.indices) {
            val type = types[i] ?: continue
            dst.set(i, mapping(type), InsertMode.READ_ONLY)
        }
        return dst
    }

    fun filterNotNull(): ParameterList {
        val newGenerics = ArrayList<Parameter>()
        val newTypes = ArrayList<Type>()
        for (i in generics.indices) {
            val type = types[i] ?: continue
            newGenerics.add(generics[i])
            newTypes.add(type)
        }
        return ParameterList(newGenerics, newTypes)
    }

    operator fun plus(other: ParameterList): ParameterList {
        val result = ParameterList(this.generics + other.generics)
        for (i in generics.indices) {
            set(i, types[i], insertModes[i])
        }
        for (i in other.generics.indices) {
            set(i + generics.size, other.types[i], other.insertModes[i])
        }
        return result
    }

    override fun subList(fromIndex: Int, toIndex: Int): ParameterList {
        return ParameterList(
            generics.subList(fromIndex, toIndex),
            List(toIndex - fromIndex) { types[it + fromIndex]!! }
        )
    }

    fun clear() {
        insertModes.fill(InsertMode.WEAK)
        types.fill(null)
    }

    override fun toString(): String {
        return indices.joinToString(", ", "[", "]") { idx ->
            "(${insertModes[idx].symbol})${types[idx]}"
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is ParameterList &&
                other.generics == generics &&
                other.types.contentEquals(types)
    }

    override fun hashCode(): Int {
        return generics.hashCode() * 31 + types.contentHashCode()
    }

    fun readonly(): ParameterList {
        if (insertModes.all { it == InsertMode.READ_ONLY }) return this
        val copy = ParameterList(generics)
        for (i in generics.indices) {
            copy.set(i, types[i]!!, InsertMode.READ_ONLY)
        }
        return copy
    }

}