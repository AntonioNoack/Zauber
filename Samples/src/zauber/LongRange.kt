package zauber

class LongRange(val from: Long, val endExcl: Long, val step: Long) : Iterable<Long> {
    constructor(from: Long, endExcl: Long) : this(from, endExcl, 1L)

    override fun iterator(): Iterator<Long> = LongRangeIterator(from, endExcl)

    infix fun step(step: Long): LongRange = LongRange(from, endExcl, step)
}

class LongRangeIterator(var index: Long, val endExcl: Long, val step: Long = 1L) : Iterator<Long> {
    override fun hasNext(): Boolean = index < endExcl
    override fun next(): Long {
        val value = index
        index += step
        return value
    }
}

infix fun Long.until(endExcl: Long): LongRange {
    return LongRange(this, endExcl, 1L)
}

operator fun Long.rangeTo(endIncl: Long): LongRange {
    return LongRange(this, endIncl + 1, 1L)
}
