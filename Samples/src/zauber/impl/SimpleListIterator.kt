package zauber.impl

import zauber.Int
import zauber.List

class SimpleListIterator<V>(
    val base: List<V>,
    var index: Int = 0
) : Iterator<V> {

    override fun hasNext(): Boolean = index < base.size
    override fun next(): V = base[index++]
}