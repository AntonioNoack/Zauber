package zauber

interface CharSequence: List<Char> {
    fun substring(startIndex: Int, endIndexExcl: Int): String
}