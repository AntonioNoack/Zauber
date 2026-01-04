package zauber

interface Iterator<V> {
    fun hasNext(): Boolean
    fun next(): V
}

interface ListIterator<V>: Iterator<V> {
    fun hasPrevious(): Boolean
    fun previous(): V

    fun nextIndex(): Int
    fun previousIndex(): Int
}