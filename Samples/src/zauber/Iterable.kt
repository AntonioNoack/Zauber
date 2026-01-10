package zauber

interface Iterable<V> {
    operator fun iterator(): Iterator<V>

    fun all(predicate: (V) -> Boolean): Boolean {
        for (element in this) {
            if (!predicate(element)) return false
        }
        return true
    }

    fun none(predicate: (V) -> Boolean): Boolean {
        for (element in this) {
            if (predicate(element)) return false
        }
        return true
    }

    fun any(predicate: (V) -> Boolean): Boolean {
        for (element in this) {
            if (predicate(element)) return true
        }
        return false
    }

    fun firstOrNull(): V? {
        for (element in this) {
            return element
        }
        return null
    }

    fun firstOrNull(predicate: (V) -> Boolean): V? {
        for (element in this) {
            if (predicate(element)) return element
        }
        return null
    }

    fun first(): V {
        return iterator().next()
    }

    fun lastOrNull(): V? {
        var value: V? = null
        for (element in this) {
            value = element
        }
        return value
    }

    fun lastOrNull(predicate: (V) -> Boolean): V? {
        var value: V? = null
        for (element in this) {
            if (predicate(element)) value = element
        }
        return value
    }

    fun last(): V {
        lateinit var value: V
        for (element in this) {
            value = element
        }
        return value
    }
}