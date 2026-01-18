package zauber

class IntRange(from: Int, endInclusive: Int) : IntProgression(from, endInclusive, 1) {
    operator fun contains(value: Int): Boolean = value >= from && value <= endInclusive
    infix fun step(step: Int): IntRange = IntProgression(from, endInclusive, step)
}

class IntProgression(val from: Int, val endInclusive: Int, val step: Int) : Iterable<Int> {

    override fun iterator(): Iterator<Int> = IntRangeIterator(from, endInclusive, step)
    fun reversed() = IntProgression(from, endInclusive, -step)

    val first get() = from
    val last get() = endInclusive
}

class IntRangeIterator(var index: Int, val endInclusive: Int, val step: Int) : Iterator<Int> {
    override fun hasNext(): Boolean = if (step > 0) index <= endInclusive else index >= endInclusive
    override fun next(): Int {
        val value = index
        index = value + step
        return value
    }
}

infix fun Int.until(endExclusive: Int): IntRange {
    return IntRange(this, endExclusive - 1, 1)
}

operator fun Int.rangeTo(endInclusive: Int): IntRange {
    return IntRange(this, endInclusive, 1)
}

infix fun Int.downTo(endInclusive: Int): IntProgression {
    return IntProgression(this, endInclusive, -1)
}
