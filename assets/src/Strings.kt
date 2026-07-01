
external class Char(val content: Char) {
    external fun compareTo(other: Char): Int
}

interface CharSequence {}

class String(val bytes: ByteArray) {

}

external fun println(arg0: String)