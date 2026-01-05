package zauber

import zauber.impl.SimpleListIterator

// todo is this a MutableList? Kind of yes, kind of no...
class Array<V>(override val size: Int) : List<V> {
    external override operator fun get(index: Int): V
    external operator fun set(index: Int, value: V)

    external fun copyOf(): Array<V>
    external fun copyOf(newSize: Int): Array<V>
    override fun isEmpty(): Boolean = size == 0

    override fun iterator(): Iterator<V> = SimpleListIterator(this, 0)
}

fun <V> arrayOfNulls(size: Int): Array<V?> {
    return Array<V?>(size)
}