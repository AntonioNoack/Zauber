package zauber

open class Any {
    open fun toString(): String {
        return "@${hashCode()}"
    }

    open external fun hashCode(): Int

    open fun equals(other: Any?): Boolean = (this === other)
}

object Unit

interface List<V> {
    val size: Int
    fun get(index: Int): V
}

interface MutableList<V>: List<V> {
    fun set(index: Int, value: V)
}

class Array<V>(val size: Int): MutableList<V> {
    external override fun get(index: Int): V
    external override fun set(index: Int, value: V)

    fun copyOfRange(i0: Int, i1: Int): Array<V> {
        val clone = Array<V>(i1-i0)
        var i = i0
        while (i < i1) {
            clone[i - i0] = this[i]
            i++
        }
        return clone
    }
}