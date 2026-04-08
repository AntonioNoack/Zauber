package me.anno.utils

object Maths {
    fun clamp(x: Int, min: Int, max: Int): Int {
        check(min <= max)
        return if (x < min) min else if (x < max) x else max
    }
}