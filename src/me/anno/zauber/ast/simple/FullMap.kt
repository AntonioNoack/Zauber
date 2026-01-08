package me.anno.zauber.ast.simple

class FullMap<K, V>(val value: V) : Map<K, V> {

    override val size: Int
        get() = 1

    override val keys: Set<K>
        get() = throw IllegalArgumentException("Cannot access keys of FullMap")
    override val values: Collection<V>
        get() = listOf(value)

    override val entries: Set<Map.Entry<K, V>> = object : Set<Map.Entry<K, V>> {
        override val size: Int = 1

        private val entry = object : Map.Entry<K, V> {
            override val key: K
                get() = throw IllegalArgumentException("Cannot access key of FullMap")
            override val value: V
                get() = this@FullMap.value
        }

        override fun isEmpty(): Boolean = false
        override fun contains(element: Map.Entry<K, V>): Boolean = throw NotImplementedError()
        override fun iterator(): Iterator<Map.Entry<K, V>> = listOf(entry).iterator()

        override fun containsAll(elements: Collection<Map.Entry<K, V>>): Boolean =
            elements.all { contains(it) }
    }

    override fun isEmpty(): Boolean = false
    override fun containsKey(key: K): Boolean = true
    override fun containsValue(value: V): Boolean = (value == this.value)

    override fun get(key: K): V? = value

}