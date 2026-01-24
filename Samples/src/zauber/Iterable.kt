package zauber

interface Iterable<V> {
    operator fun iterator(): Iterator<V>

    inline fun all(predicate: (V) -> Boolean): Boolean {
        for (element in this) {
            if (!predicate(element)) return false
        }
        return true
    }

    inline fun none(predicate: (V) -> Boolean): Boolean {
        for (element in this) {
            if (predicate(element)) return false
        }
        return true
    }

    inline fun any(predicate: (V) -> Boolean): Boolean {
        for (element in this) {
            if (predicate(element)) return true
        }
        return false
    }

    inline fun firstOrNull(): V? {
        for (element in this) {
            return element
        }
        return null
    }

    inline fun firstOrNull(predicate: (V) -> Boolean): V? {
        for (element in this) {
            if (predicate(element)) return element
        }
        return null
    }

    inline fun first(): V {
        return iterator().next()
    }

    inline fun lastOrNull(): V? {
        var value: V? = null
        for (element in this) {
            value = element
        }
        return value
    }

    inline fun lastOrNull(predicate: (V) -> Boolean): V? {
        var value: V? = null
        for (element in this) {
            if (predicate(element)) value = element
        }
        return value
    }

    inline fun last(): V {
        lateinit var value: V
        for (element in this) {
            value = element
        }
        return value
    }
}
