package me.anno.utils

object NumberUtils {
    fun Float.f1() = "%.1f".format(this)
    fun Float.f3() = "%.3f".format(this)

    fun Boolean.toInt() = if (this) 1 else 0
}