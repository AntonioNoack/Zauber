package me.anno.libraries

import me.anno.zauber.logging.LogManager

/**
 * Parses JSON into Map<String,?>, Array<?>, true, false, null, Long, Double
 * */
object JSONParser {

    private val LOGGER = LogManager.getLogger(JSONParser::class)
    private val builder = ThreadLocal.withInitial { StringBuilder() }

    fun parseJSON(text: String): Any? {
        try {
            val (i, value) = readValue(text, 0)
            return if (skipWhitespace(text, i) >= text.length) value
            else text // not JSON, just a string
        } catch (e: Exception) {
            LOGGER.warn("Invalid JSON: $e")
            return null
        }
    }

    data class JSONResult<V>(val i1: Int, val value: V)

    fun skipWhitespace(text: String, i: Int): Int {
        var i = i
        while (i < text.length && text[i].isWhitespace()) i++
        return i
    }

    fun readValue(text: String, i0: Int): JSONResult<*> {
        val i = skipWhitespace(text, i0)
        if (i >= text.length) return JSONResult(i, null)

        return when (text[i]) {
            '{' -> readObject(text, i)
            '[' -> readArray(text, i)
            't' -> readTrue(text, i)
            'f' -> readFalse(text, i)
            'n' -> readNull(text, i)
            in '0'..'9', in ".+-" -> readNumber(text, i)
            '"', '\'' -> readString(text, i)
            else -> error("Invalid JSON value at position $i in '$text'")
        }
    }

    fun readNumber(text: String, i0: Int): JSONResult<Number> {
        var i0 = skipWhitespace(text, i0)
        if (text[i0] == '+') i0++
        var i = i0
        while (i < text.length) {
            when (text[i]) {
                in '0'..'9', in "+-Ee." -> i++
                else -> break
            }
        }

        val value = text.substring(i0, i)
        val isInt = value.all { it in '0'..'9' || it in "-" }
        val number = (if (isInt) value.toLongOrNull() else null) ?: value.toDouble()
        return JSONResult(i, number)
    }

    fun readTrue(text: String, i0: Int): JSONResult<Boolean> {
        val i = skipWhitespace(text, i0)
        check(text.startsWith("true", i))
        return JSONResult(i + 4, true)
    }

    fun readFalse(text: String, i0: Int): JSONResult<Boolean> {
        val i = skipWhitespace(text, i0)
        check(text.startsWith("false", i))
        return JSONResult(i + 5, false)
    }

    fun readNull(text: String, i0: Int): JSONResult<Nothing?> {
        val i = skipWhitespace(text, i0)
        check(text.startsWith("null", i))
        return JSONResult(i + 4, null)
    }

    private fun isNameChar(c: Char): Boolean {
        return c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "-_"
    }

    fun readObject(text: String, i0: Int): JSONResult<Map<String, Any?>> {
        var i = skipWhitespace(text, i0)
        check(text.startsWith("{", i))
        i++ // skip '{'
        val result = HashMap<String, Any?>()
        while (true) {
            i = skipWhitespace(text, i)
            if (i >= text.length) error("Unfinished array at $i0 in '$text'")
            if (text[i] == '}') break

            val key = if (text[i] in "\"'") {
                val key = readString(text, i)
                i = skipWhitespace(text, key.i1)
                check(text.startsWith(":", i)) { "Expected ':' at $i in '$text'" }
                i++ // skip colon
                key.value
            } else {
                // needed for TOML
                val end = text.indexOf(':', i)
                check(end > i) { "Expected ':' after $i in '$text'" }
                val key = text.substring(i, end).trimEnd()
                check(key.all { isNameChar(it) }) {
                    "Invalid name '$key' at $i in '$text'"
                }
                i = end + 1 // skip name and colon
                key
            }

            val (j, value) = readValue(text, i)
            result[key] = value

            i = skipWhitespace(text, j)
            if (text[i] == ',') {
                i++
            } else break
        }

        check(text[i] == '}') { "Incomplete object at $i in '$text'" }
        return JSONResult(i + 1, result)
    }

    fun readArray(text: String, i0: Int): JSONResult<List<Any?>> {
        var i = skipWhitespace(text, i0)
        check(text.startsWith("[", i))
        i++ // skip '['
        val result = ArrayList<Any?>()
        while (true) {
            i = skipWhitespace(text, i)
            if (i >= text.length) error("Unfinished array at $i0 in '$text'")
            if (text[i] == ']') break

            val (j, value) = readValue(text, i)
            result.add(value)

            i = skipWhitespace(text, j)
            if (text[i] == ',') {
                i++
                continue
            }
        }

        check(text[i] == ']') { "Incomplete array at $i in '$text'" }
        return JSONResult(i + 1, result)
    }

    fun readString(text: String, i0: Int): JSONResult<String> {
        var i = skipWhitespace(text, i0)
        val start = text[i++]
        check(start in "\"'")

        val builder = builder.get()
        builder.clear() // just in case

        while (true) {
            val letter = when (val c = text[i++]) {
                start -> {
                    val value = builder.toString()
                    builder.clear()
                    return JSONResult(i, value)
                }
                '\\' -> when (val c2 = text[i++]) {
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    'b' -> '\b'
                    'f' -> '\u000C'
                    '0' -> '\u0000'
                    'u' -> {
                        // todo Chatchy says we don't support Unicode correctly?
                        val value = text
                            .substring(i, i + 4)
                            .toInt(16)
                        i += 4
                        value.toChar()
                    }
                    else -> c2
                }
                else -> c
            }
            builder.append(letter)
        }
    }

}