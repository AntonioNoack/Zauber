package zauber
external class Char(val content: Char) {
    external fun compareTo(other: Char): Int
}

interface CharSequence {}

class String(val bytes: ByteArray) {
    // todo all what we need: charAt(), substring(), trim(), toX(), toXOrNull()
}

external fun println(arg0: String)