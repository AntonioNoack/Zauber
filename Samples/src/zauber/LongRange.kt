package zauber

class LongRange(from: Long, endInclusive: Long) : LongProgression(from, endInclusive, 1L) {
    operator fun contains(value: Long): Boolean = value >= from && value <= endInclusive
    infix fun step(step: Long): LongRange = LongProgression(from, endInclusive, step)
}

class LongProgression(val from: Long, val endInclusive: Long, val step: Long) : Iterable<Long> {

    override fun iterator(): Iterator<Long> = LongRangeIterator(from, endInclusive, step)
    fun reversed() = LongProgression(from, endInclusive, -step)

    val first get() = from
    val last get() = endInclusive
}

class LongRangeIterator(var index: Long, val endInclusive: Long, val step: Long) : Iterator<Long> {
    override fun hasNext(): Boolean = if (step > 0L) index <= endInclusive else index >= endInclusive
    override fun next(): Long {
        val value = index
        index = value + step
        return value
    }
}

infix fun Long.until(endExclusive: Long): LongRange {
    return LongRange(this, endExclusive - 1, 1)
}

operator fun Long.rangeTo(endInclusive: Long): LongRange {
    return LongRange(this, endInclusive, 1)
}

infix fun Long.downTo(endInclusive: Long): LongProgression {
    return LongProgression(this, endInclusive, -1)
}
