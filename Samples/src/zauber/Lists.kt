package zauber

fun <V> emptyList(): List<V> = Array(0)

interface List<V> : Collection<V> {

    operator fun get(index: Int): V

    fun getOrNull(index: Int): V? {
        return if (index in 0 until size) this[index] else null
    }

    override fun iterator(): Iterator<V> = listIterator(0)
    fun listIterator(startIndex: Int = 0): ListIterator<V>

    fun indexOf(element: V): Int
    fun lastIndexOf(element: V): Int

    override fun contains(element: V): Boolean = indexOf(element) >= 0
    override fun firstOrNull(): V? = getOrNull(0)
    override fun first(): V = get(0)
    override fun lastOrNull(): V? = getOrNull(lastIndex)
    override fun last(): V = get(lastIndex)
}

val List<*>.indices: IntRange get() = 0 until size

val List<*>.lastIndex: Int
    get() = size - 1

fun <V> List<V>.withIndex(): List<IndexedValue<V>> {
    return List(size) {
        IndexedValue(it, this[it])
    }
}

fun <V> listOf(vararg v: V): List<V> {
    val dst = ArrayList<V>(v.size)
    dst.addAll(v)
    return dst
}

fun <V> List(size: Int, generator: (Int) -> V): List<V> {
    val dst = ArrayList<V>(size)
    for (i in 0 until size) {
        dst.add(generator(i))
    }
    return dst
}

inline fun <V> List<V>.filter(predicate: (V) -> Boolean): List<V> {
    val result = ArrayList<V>(size)
    for (i in indices) {
        val element = this[i]
        if (predicate(element)) {
            result.add(element)
        }
    }
    return result
}

inline fun <V, R> List<V>.mapIndexed(transform: (Int, V) -> R): List<R> {
    return List<R>(size) { transform(it, this[it]) }
}

inline fun <V, R> List<V>.map(transform: (V) -> R): List<R> {
    return List<R>(size) { transform(this[it]) }
}

inline fun <V, R : Any> List<V>.mapNotNull(transform: (V) -> R?): List<R> {
    val result = ArrayList<R>(size)
    for (i in indices) {
        val element = transform(this[i])
        if (element != null) result.add(element)
    }
    return result
}

fun <V> List<V>.joinToString(separator: String = ",", prefix: String = "[", postfix: String = "]"): String {
    var result = prefix
    for (i in indices) {
        if (i > 0) result += separator
        result += this[i]
    }
    result += postfix
    return result
}

fun <V> List<V>.joinToString(convertToString: (V) -> String): String {
    return joinToString(", ", convertToString)
}

fun <V> List<V>.joinToString(separator: String, convertToString: (V) -> String): String {
    return joinToString(separator, "[", "]", convertToString)
}

fun <V> List<V>.joinToString(
    separator: String,
    prefix: String,
    postfix: String,
    convertToString: (V) -> String
): String {
    var result = prefix
    for (i in indices) {
        if (i > 0) result += separator
        result += convertToString(this[i])
    }
    result += postfix
    return result
}

operator fun <V> List<V>.plus(other: List<V>): List<V> {
    val result = ArrayList<V>(size + other.size)
    result.addAll(this)
    result.addAll(other)
    return result
}

data class IndexedValue<V>(val index: Int, val value: V)

// todo mutable iterable...
interface MutableList<V> : List<V> {
    operator fun set(index: Int, value: V): V

    fun add(element: V): Boolean
    fun add(index: Int, element: V): Boolean
    fun remove(element: V): Boolean
    fun removeAt(index: Int): V

    fun removeFirst(): V
    fun removeLast(): V

    fun addAll(elements: Collection<V>): Boolean {
        var changed = false
        for (element in elements) {
            if (add(element)) changed = true
        }
        return changed
    }

    fun removeFirstOrNull(): V? {
        return if (isEmpty()) null else removeFirst()
    }

    fun removeLastOrNull(): V? {
        return if (isEmpty()) null else removeLast()
    }

    fun removeAll(elements: Collection<V>): Boolean {
        val iterator = listIterator(0)
        var changed = false
        while (iterator.hasNext()) {
            val v = iterator.next()
            if (v in elements) {
                iterator.remove()
                changed = true
            }
        }
        return changed
    }

    fun retainAll(elements: Collection<V>): Boolean {
        val iterator = listIterator(0)
        var changed = false
        while (iterator.hasNext()) {
            val v = iterator.next()
            if (v !in elements) {
                iterator.remove()
                changed = true
            }
        }
        return changed
    }

    override fun listIterator(startIndex: Int): MutableListIterator<V>
}

fun repeat(count: Int, runnable: () -> Unit) {
    for (i in 0 until count) runnable()
}