package zauber

fun check(b: Boolean) {
    if (!b) throw IllegalStateException()
}

inline fun check(b: Boolean, getMessage: () -> String) {
    if (!b) throw IllegalStateException(getMessage())
}