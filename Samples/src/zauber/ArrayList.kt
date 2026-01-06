package zauber

import zauber.impl.SimpleListIterator
import kotlin.math.max

class ArrayList<V>(capacity: Int = 16) : MutableList<V> {

    val content = Array<V>(capacity)

    override var size: Int = 0
        private set

    override fun isEmpty(): Boolean = size == 0

    override fun get(index: Int): V = content[index]

    override fun set(index: Int, value: V): V {
        val prev = content[index]
        content[index] = value
        return prev
    }

    private fun ensureExtra(extra: Int) {
        if (this.size + extra >= content.size) {
            val newSize = max(16, max(content.size * 2, content.size + extra))
            content = content.copyOf(newSize)
        }
    }

    override fun add(element: V): Boolean {
        ensureExtra(1)
        content[size++] = element
        return true
    }

    override fun addAll(elements: Collection<V>): Boolean {
        if(elements.isEmpty()) return false
        ensureExtra(elements.size)
        for (element in elements) add(element)
        return true
    }

    override fun listIterator(startIndex: Int): Iterator<V> = SimpleListIterator(this, startIndex)
}