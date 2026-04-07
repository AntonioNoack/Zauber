package me.anno.zauber.utils

object CollectionUtils {

    inline fun <V, reified W> List<V>.mapArray(mapping: (V) -> W): Array<W> {
        return Array(size) { mapping(this[it]) }
    }

    inline fun <V, reified W> Array<V>.mapArray(mapping: (V) -> W): Array<W> {
        return Array(size) { mapping(this[it]) }
    }

    fun <K, V> HashMap<K, V>.getOrPutRecursive(
        key: K,
        createInstance: (K) -> V,
        initializeInstance: (K, V) -> Unit
    ): V {
        var created = false
        val instance = getOrPut(key) {
            created = true
            createInstance(key)
        }
        if (created) {
            initializeInstance(key, instance)
        }
        return instance
    }

}