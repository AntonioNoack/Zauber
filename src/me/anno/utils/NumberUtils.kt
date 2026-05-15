package me.anno.utils

object NumberUtils {
    fun Float.f1() = "%.1f".format(this)
    fun Float.f3() = "%.3f".format(this)

    fun Boolean.toInt() = if (this) 1 else 0

    fun pack64(i0: Int, i1: Int): Long {
        return i1.toLong().shl(32) + i0.toLong().and(0xffff_ffffL)
    }

    fun unpack64I0(origin: Long): Int {
        return origin.toInt()
    }

    fun unpack64I1(origin: Long): Int {
        return origin.shr(32).toInt()
    }
}