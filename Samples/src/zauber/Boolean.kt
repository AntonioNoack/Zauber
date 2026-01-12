package zauber

enum class Boolean {
    FALSE,
    TRUE;

    fun not(): Boolean = if (this == FALSE) TRUE else FALSE
}

