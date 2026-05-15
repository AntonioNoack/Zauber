package me.anno.utils

object NumberUtils {
    fun Float.f1() = "%.1f".format(this)
    fun Float.f3() = "%.3f".format(this)

    fun Boolean.toInt() = if (this) 1 else 0

    fun pack64(high: Int, low: Int): Long {
        return (high.toLong() shl 32) + low.toLong().and(0xffffffffL)
    }
}