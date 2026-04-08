package me.anno.utils

class LazyMap<K, V>(val generator: (K) -> V) : Map<K, V> {

    private val content = HashMap<K, V>()

    override val size: Int
        get() = content.size
    override val keys: Set<K>
        get() = content.keys
    override val values: Collection<V>
        get() = content.values
    override val entries: Set<Map.Entry<K, V>>
        get() = content.entries

    override fun isEmpty(): Boolean = content.isEmpty()
    override fun containsKey(key: K): Boolean = true
    override fun containsValue(value: V): Boolean = throw NotImplementedError()

    override fun get(key: K): V? {
        return content.getOrPut(key) { generator(key) }
    }

}