package me.anno.utils

object StringUtils {
    fun String.capitalize1(): String {
        val first = this[0]
        return if (first in 'a'..'z') {
            val tmp = StringBuilder(length)
            tmp.append(first + ('A' - 'a'))
            tmp.append(this, 1, length)
            tmp.toString()
        } else this
    }
}