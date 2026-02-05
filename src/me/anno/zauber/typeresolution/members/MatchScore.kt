package me.anno.zauber.typeresolution.members

import kotlin.math.min

class MatchScore(size: Int) : Comparable<MatchScore> {

    val entries = IntArray(size)
    var index = -1

    fun inc() {
        if (index < 0) return
        entries[index]++
    }

    fun at(index: Int): MatchScore {
        this.index = index
        return this
    }

    override fun compareTo(other: MatchScore): Int {
        val a = entries
        val b = other.entries
        for (i in 0 until min(a.size, b.size)) {
            val c = a[i].compareTo(b[i])
            if (c != 0) return c
        }
        return a.size.compareTo(b.size)
    }
}