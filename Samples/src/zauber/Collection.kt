package zauber

interface Collection<V> : Iterable<V> {

    val size: Int

    fun isEmpty(): Boolean = size == 0
    fun isNotEmpty(): Boolean = !isEmpty()

    operator fun contains(element: V): Boolean

    fun containsAll(elements: Collection<V>): Boolean {
        return elements.all { contains(it) }
    }
}