package zauber

interface Collection<V> : Iterable<V> {
    val size: Int
    operator fun contains(element: V): Boolean
    fun containsAll(elements: Collection<V>): Boolean
}