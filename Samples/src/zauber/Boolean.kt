package zauber

enum class Boolean {
    FALSE,
    TRUE;

    fun not(): Boolean = native("!this")
}

