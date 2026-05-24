package me.anno.utils

class IntArrayList(capacity: Int) {
    var values = IntArray(capacity)
    var size = 0

    fun add(element: Int) {
        if (size >= values.size) values = values.copyOf(values.size * 2)
        values[size++] = element
    }

    operator fun set(index: Int, element: Int) {
        values[index] = element
    }

    fun toIntArray() = values.copyOf(size)
}