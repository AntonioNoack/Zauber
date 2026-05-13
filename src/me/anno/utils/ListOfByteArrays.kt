package me.anno.utils

class ListOfByteArrays(val bytes: ByteArrayOutputStream2) {

    // todo could be optimized
    private val lengths = ArrayList<Int>()

    fun finishBody() {
        lengths.add(bytes.size)
    }

    val size get() = lengths.size

    fun getI0(i: Int) = if (i == 0) 0 else lengths[i - 1]
    fun getI1(i: Int) = lengths[i]
}