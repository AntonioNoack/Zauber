package zauber

import zauber.impl.SimpleListIterator

class Array<V>(override val size: Int) : List<V> {
    private val address: NativePtr = calloc(sizeOf<V>() * size)

    external override operator fun get(index: Int): V
    external operator fun set(index: Int, value: V)

    external fun copyOf(): Array<V>
    external fun copyOf(newSize: Int): Array<V>
    override fun isEmpty(): Boolean = size == 0

    override fun listIterator(startIndex: Int) = SimpleListIterator<V>(this, startIndex)
}

fun <V> arrayOf(vararg values: V): Array<V> {
    return values
}

fun <V> arrayOfNulls(size: Int): Array<V?> {
    return Array<V?>(size)
}

/* todo V needs Ownership information, not just type information */
external fun <V> sizeOf(): Int
external fun calloc(sizeOf: Int): NativePtr