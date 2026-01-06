package zauber

fun check(b: Boolean) {
    if (!b) throw IllegalStateException()
}