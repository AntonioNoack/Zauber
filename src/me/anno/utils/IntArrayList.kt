package me.anno.utils

import kotlin.math.max

class IntArrayList(capacity: Int) {
    var values = IntArray(max(capacity, 4))
    var size = 0

    fun add(element: Int) {
        if (size >= values.size) values = values.copyOf(values.size * 2)
        values[size++] = element
    }

    operator fun set(index: Int, element: Int) {
        values[index] = element
    }

    operator fun get(index: Int) = values[index]
    fun last() = values[size - 1]

    fun toIntArray() = values.copyOf(size)

    override fun toString(): String {
        return List(size) { values[it].toString() }.joinToString(", ", "[", "]")
    }

}