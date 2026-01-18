package zauber

interface Map<K, V> : Collection<Map.Entry<K, V>> {
    operator fun get(key: K): V?

    interface Entry<K, V> {
        val key: K
        val value: V
    }

    val keys: Set<K>
    val values: Collection<V>

    operator fun contains(key: K): Boolean = key in keys
    fun containsKey(key: K): Boolean = key in keys
    fun containsValue(value: V): Boolean = value in values
}

interface MutableMap<K, V> : Map<K, V> {
    operator fun set(key: K, value: V): V?

    @Deprecated("For Kotlin compatibility")
    fun put(key: K, value: V) = set(key, value)

    override fun iterator(): MutableIterator<Map.Entry<K, V>>

    inline fun getOrPut(key: K, defaultValue: () -> V): V {
        if (key !in this) {
            val value = defaultValue()
            put(key, value)
            return value
        } else {
            return this[key] as V
        }
    }
}

private class TrivialMap<K, V>(vararg val entries: Pair<K, V>) : Map<K, V> {
    override val size: Int
        get() = entries.size

    override fun get(key: K): V? {
        for (entry in entries) {
            if (key == entry.first) return entry.second
        }
        return null
    }

    override fun contains(element: Map.Entry<K, V>): Boolean {
        TODO("Not yet implemented")
    }

    override fun containsAll(elements: Collection<Map.Entry<K, V>>): Boolean {
        TODO("Not yet implemented")
    }

    override fun iterator(): MutableIterator<Map.Entry<K, V>> {
        TODO("Not yet implemented")
    }
}

fun <K, V> mapOf(vararg entries: Pair<K, V>): Map<K, V> {
    return TrivialMap<K, V>(*entries)
}

fun <K, V, R> Map<K, V>.mapValues(mapping: (Map.Entry<K, V>) -> R): Map<K, R> {
    val result = HashMap<K, R>()
    for ((k, v) in this) {
        result[k] = mapping(v)
    }
    return result
}