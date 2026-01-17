package zauber

import zauber.impl.SimpleMutableListIterator
import zauber.math.max
import kotlin.ranges.downTo

class StringBuilder(capacity: Int = 16) : CharSequence, MutableList<Char> {

    private var content = ByteArray(capacity)

    override fun substring(startIndex: Int, endIndexExcl: Int): String {
        TODO("Not yet implemented")
    }

    override fun get(index: Int): Char = content[index].toChar()

    override fun listIterator(startIndex: Int): MutableListIterator<Char> =
        SimpleMutableListIterator(this, startIndex)

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
        val prevCapacity = content.size
        if (prevCapacity <= size + extra) return
        val newSize = max(size + extra, prevCapacity * 2, 16)
        content = content.copyOf(newSize)
    }

    fun append(char: Char): This {
        ensureExtraSize(1)
        content[size++] = char
        return this
    }

    fun append(string: String): This {
        ensureExtraSize(string.length)
        val size0 = size
        for (i in string.indices) {
            content[size0 + i] = string[i].toByte()
        }
        size = size0 + string.length
        return this
    }

    fun append(other: Any?): This {
        return append(other.toString())
    }

}