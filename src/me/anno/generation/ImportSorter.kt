package me.anno.generation

import kotlin.math.min

object ImportSorter : Comparator<List<String>> {
    override fun compare(p0: List<String>, p1: List<String>): Int {
        for (i in 0 until min(p0.size, p1.size)) {
            val result = p0[i].compareTo(p1[i])
            if (result != 0) return result
        }
        return p0.size.compareTo(p1.size)
    }
}