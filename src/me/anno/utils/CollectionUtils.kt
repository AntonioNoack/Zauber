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


    @JvmStatic
    fun <V : Any> Collection<V>.sortedByTopology(getDependencies: (V) -> Collection<V>?): List<V>? {
        return sortByTopology1(toMutableList(), getDependencies, false)
    }

    @JvmStatic
    fun <V : Any> Collection<V>.sortedByParent(getParent: (V) -> V?): List<V>? {
        return sortByParent1(toMutableList(), getParent, false)
    }

    /**
     * returns an order such that elements without dependencies come first,
     * and elements with dependencies come after their dependencies;
     * https://en.wikipedia.org/wiki/Topological_sorting
     *
     * returns null if there is any dependency
     * */
    @JvmStatic
    fun <V : Any> MutableList<V>.sortByTopology(getDependencies: (V) -> Collection<V>?): List<V>? {
        return sortByTopology1(this, getDependencies, true)
    }

    @JvmStatic
    private fun <V : Any> sortByTopology1(
        list: MutableList<V>, getDependencies: (V) -> Collection<V>?,
        restoreOriginal: Boolean
    ): List<V>? {
        return object : TopologicalSort<V, MutableList<V>>(list) {
            override fun visitDependencies(node: V): Boolean {
                val dependencies = getDependencies(node)
                return dependencies != null && dependencies.any { visit(it) }
            }
        }.finish(restoreOriginal)
    }

    /**
     * returns a list, where parents always come before their children;
     * returns null, if no such list exists (dependency cycles)
     * */
    @JvmStatic
    fun <V : Any> MutableList<V>.sortByParent(getParent: (V) -> V?): List<V>? {
        return sortByParent1(this, getParent, true)
    }

    @JvmStatic
    private fun <V : Any> sortByParent1(
        list: MutableList<V>, getParent: (V) -> V?,
        restoreOriginal: Boolean
    ): List<V>? {
        return object : TopologicalSort<V, MutableList<V>>(list) {
            override fun visitDependencies(node: V): Boolean {
                val parent = getParent(node)
                return parent != null && visit(parent)
            }
        }.finish(restoreOriginal)
    }

    fun <V, K> List<V>.groupByMutable(selector: (V) -> K): HashMap<K, ArrayList<V>> {
        val map = HashMap<K, ArrayList<V>>()
        forEach { value ->
            map.getOrPut(selector(value), ::ArrayList)
                .add(value)
        }
        return map
    }


}