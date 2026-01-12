package zauber

interface Iterator<V> {
    fun hasNext(): Boolean
    fun next(): V
}

interface ListIterator<V> : Iterator<V> {
    fun hasPrevious(): Boolean
    fun previous(): V

    fun nextIndex(): Int
    fun previousIndex(): Int
}

interface MutableIterator<V> : Iterator<V> {
    /**
     * removes the last seen element
     * calling it twice is illegal
     * */
    fun remove()
}

interface MutableListIterator<V> : ListIterator<V>, MutableIterator<V>

abstract class IteratorUntilNull<V : Any> : Iterator<V> {

    abstract fun nextOrNull(): V?

    private fun generateIfNeeded() {
        if (!hasCheckedNext && next == null) {
            next = nextOrNull()
            hasCheckedNext = true
        }
    }

    private var next: V? = null
    private var hasCheckedNext = false
    final override fun hasNext(): Boolean {
        generateIfNeeded()
        return next != null
    }

    final override fun next(): V {
        generateIfNeeded()
        val value = next!!
        hasCheckedNext = false
        return value
    }
}
