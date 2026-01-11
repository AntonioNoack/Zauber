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

interface MutableCollection<V> : Collection<V> {

    fun add(value: V): Boolean

    fun addAll(elements: Collection<V>): Boolean {
        // base implementation: can be overridden, if there is a faster way
        var changed = false
        for (element in elements) {
            if (add(element)) changed = true
        }
        return changed
    }

    fun remove(value: V): Boolean

    fun removeAll(elements: Collection<V>): Boolean {
        // depending on size of elements and this, we should use the faster way...
        if (elements.size < size * 4) {
            var changed = false
            for (element in elements) {
                if (remove(element)) changed = true
            }
            return changed
        } else {
            return removeIf { it in elements }
        }
    }

    fun retainAll(elements: Collection<V>): Boolean {
        return removeIf { it !in elements }
    }

    fun removeIf(predicate: (V) -> Boolean): Boolean {
        val iterator = iterator()
        var changed = false
        while (iterator.hasNext()) {
            val v = iterator.next()
            if (predicate(v)) {
                iterator.remove()
                changed = true
            }
        }
        return changed
    }

    fun clear(): Boolean {
        // slow base implementation
        return removeIf { true }
    }

    override fun iterator(): MutableIterator<V>
}