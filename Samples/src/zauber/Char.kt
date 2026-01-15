package zauber

@Deprecated("This only exists for compatibility")
value class Char(val value: NativeI8) {
    fun isLetter() = this in ('a'..'z') || this in ('A'..'Z')
    fun isDigit() = this in ('0'..'9')
    fun isLetterOrDigit() = isLetter() || isDigit()
    fun isWhitespace() = this in " \t\r\n\b"

    external operator fun plus(other: Int): Char
    external operator fun minus(other: Int): Char

    infix fun rangeTo(other: Char) = CharRange(this, other + 1)
    infix fun until(other: Char) = CharRange(this, other)

    infix fun inc() = this + 1
    infix fun dec() = this - 1
}