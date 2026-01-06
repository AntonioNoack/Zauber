package zauber

class String(
    private val content: ByteArray,
    private val offset: Int,
    override val size: Int
) : CharSequence {

    constructor(content: ByteArray): this(content, 0, content.size)

    @Deprecated("Use size for API consistency")
    val length: Int get() = size

    override fun get(index: Int): Char {
        check(index in 0 until size)
        return content[offset + index].toInt().and(255).toChar()
    }

    fun substring(startIndex: Int): String = substring(startIndex, size)
    fun substring(startIndex: Int, endIndex: Int): String {
        check(startIndex <= endIndex)
        check(startIndex >= 0)
        check(endIndex <= size)
        return String(content, startIndex + offset, endIndex - startIndex)
    }

    operator fun plus(other: Any?): String {
        // todo we could avoid a lot of StringBuilder complexity by concatenating Strings only when we need it...
        TODO("Implement String.plus")
    }

    fun format(vararg args: Any?): String {
        TODO("Implement String.format()")
    }
}
