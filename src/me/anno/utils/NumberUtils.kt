package me.anno.utils

import kotlin.math.nextDown
import kotlin.math.nextUp

object NumberUtils {
    fun Float.f1() = "%.1f".format(this)
    fun Float.f3() = "%.3f".format(this)

    fun Boolean.toInt(ifTrue: Int = 1) = if (this) ifTrue else 0

    fun pack64(i0: Int, i1: Int): Long {
        return i1.toLong().shl(32) + i0.toLong().and(0xffff_ffffL)
    }

    fun unpack64I0(origin: Long): Int {
        return origin.toInt()
    }

    fun unpack64I1(origin: Long): Int {
        return origin.shr(32).toInt()
    }

    fun Long.toFloatCeil(): Float {
        val asFloat = toFloat()
        val checkedInt = asFloat.toLong()
        return if (checkedInt < this) {
            asFloat.nextUp()
        } else asFloat
    }

    fun Long.toDoubleCeil(): Double {
        val asFloat = toDouble()
        val checkedInt = asFloat.toLong()
        return if (checkedInt < this) {
            asFloat.nextUp()
        } else asFloat
    }

    fun ULong.toFloatFloor(): Float {
        val asFloat = toFloat()
        val checkedInt = asFloat.toULong()
        return if (checkedInt > this) {
            asFloat.nextDown()
        } else asFloat
    }

    fun ULong.toDoubleFloor(): Double {
        val asFloat = toDouble()
        val checkedInt = asFloat.toULong()
        return if (checkedInt > this) {
            asFloat.nextDown()
        } else asFloat
    }
}