package me.anno.zauber.utils

object NumberUtils {
    fun Float.f1() = "%.1f".format(this)
    fun Float.f3() = "%.3f".format(this)
}