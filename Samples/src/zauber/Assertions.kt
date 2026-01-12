package zauber

/**
 * Use this to check state
 * */
fun check(b: Boolean) {
    if (!b) throw IllegalStateException()
}

/**
 * Use this to check state
 * */
inline fun check(b: Boolean, getMessage: () -> String) {
    if (!b) throw IllegalStateException(getMessage())
}

/**
 * Use this to check function inputs
 * */
fun require(b: Boolean) {
    if (!b) throw IllegalArgumentException()
}

/**
 * Use this to check function inputs
 * */
inline fun require(b: Boolean, getMessage: () -> String) {
    if (!b) throw IllegalArgumentException(getMessage())
}