package zauber.impl

import zauber.Int
import zauber.List
import zauber.MutableListIterator

open class SimpleListIterator<V>(
    val base: List<V>,
    var index: Int = 0
) : ListIterator<V> {
    override fun hasNext(): Boolean = index < base.size
    override fun next(): V = base[index++]

    override fun nextIndex() = index
    override fun previousIndex() = index - 1

    override fun hasPrevious(): Boolean = index > 0
    override fun previous(): V = base[--index]
}


class SimpleMutableListIterator<V>(
    base: MutableList<V>,
    index: Int = 0
) : SimpleListIterator<V>(base, index), MutableListIterator<V> {
    override fun remove() {
        (base as MutableList<V>).removeAt(index - 1)
    }
}