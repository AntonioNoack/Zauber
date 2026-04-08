package me.anno.utils

class PairArrayList<A, B>(capacity: Int = 16) {
    val content = ArrayList<Any?>(capacity * 2)

    val size get() = content.size shr 1
    val indices get() = 0 until size

    fun add(a: A, b: B) {
        content.add(a)
        content.add(b)
    }

    fun getA(index: Int) = content[index * 2] as A
    fun getB(index: Int) = content[index * 2 + 1] as B

    fun getAOrNull(index: Int) = content.getOrNull(index * 2) as? A
    fun getBOrNull(index: Int) = content.getOrNull(index * 2 + 1) as? B

    fun firstAOrNull() = getAOrNull(0)
    fun firstBOrNull() = getB(0)

    override fun toString(): String {
        return "[${indices.joinToString(", ") { "[${getA(it)}, ${getB(it)}]" }}]"
    }
}