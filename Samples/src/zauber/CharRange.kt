package zauber

class CharRange(val from: Char, val endExcl: Char) : Iterable<Char> {
    override fun iterator(): Iterator<Char> = CharRangeIterator(from, endExcl)
    fun reversed(): Iterable<Char> = TODO("CharRange.reversed")
}

class CharRangeIterator(var index: Char, val endExcl: Char) : Iterator<Char> {
    override fun hasNext(): Boolean = index < endExcl
    override fun next(): Char {
        val value = index
        index++
        return value
    }
}
