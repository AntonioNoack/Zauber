package zauber

import kotlin.ranges.downTo

class StringBuilder(capacity: Int = 16) : CharSequence {

    private val content = ByteArray(capacity)

    override fun substring(startIndex: Int, endIndexExcl: Int): String {
        TODO("Not yet implemented")
    }

    override fun get(index: Int): Char = content[index].toChar()

    override fun listIterator(startIndex: Int): ListIterator<Char> {
        TODO("Not yet implemented")
    }

    override fun indexOf(element: Char): Int {
        for (i in indices) {
            if (this[i] == element) return i
        }
        return -1
    }

    override fun lastIndexOf(element: Char): Int {
        for (i in lastIndex downTo 0) {
            if (this[i] == element) return i
        }
        return -1
    }

    override var size: Int = 0
        private set

    private fun ensureExtraSize(extra: Int) {
        TODO()
    }

    fun append(char: Char) {
        ensureExtraSize(1)
        content[size++] = char
    }

    fun append(string: String) {
        ensureExtraSize(string.length)
        TODO()
    }

    fun append(other: Any?) {
        append(other.toString())
    }

}