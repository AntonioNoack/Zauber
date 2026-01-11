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

interface MutableIterator<V>: Iterator<V> {
    /**
     * removes the last seen element
     * calling it twice is illegal
     * */
    fun remove()
}

interface MutableListIterator<V>: ListIterator<V>, MutableIterator<V>