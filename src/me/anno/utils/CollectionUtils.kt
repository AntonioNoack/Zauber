package me.anno.utils

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

    fun <V> ArrayList<V>.partitionBy(predicate: (V) -> Boolean): Int {
        var left = 0
        var right = size - 1
        while (left <= right) {
            while (left <= right && !predicate(this[left])) left++
            while (left <= right && predicate(this[right])) right--
            if (left >= right) break

            // swap & advance by one
            val tmp = this[left]
            this[left] = this[right]
            this[right] = tmp
            left++; right--
        }
        return left
    }

}