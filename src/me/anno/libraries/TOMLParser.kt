package me.anno.libraries

object TOMLParser {

    fun parseTOML(text: String): Map<String, Any> {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("//") }
        var group = ""
        val result = HashMap<String, Any>()
        for (line in lines) {
            when {
                line.startsWith('[') && line.endsWith(']') -> {
                    group = line.substring(1, line.length - 1) + "."
                    if (group == ".") group = ""
                }
                line.contains('=') -> {
                    val index = line.indexOf('=')
                    val key = line.substring(0, index).trim()
                    val value = line.substring(index + 1).trim()
                    result["$group$key"] = parseValue(value)
                        ?: run {
                            println("Could not parse value '$value' for $group$key")
                            continue
                        }
                }
            }
        }
        return result
    }

    private fun parseValue(line: String): Any? {
        return when {
            line == "true" -> true
            line == "false" -> false
            line.startsWith('"') && line.endsWith('"') -> parseString(line)
            line.startsWith("0b") -> line.substring(2).toLongOrNull(2)
            line.startsWith("0x") -> line.substring(2).toLongOrNull(16)
            '.' in line -> line.toDoubleOrNull()
            else -> line.toLongOrNull()
        }
    }

    private fun parseString(line: String): Any {
        // todo unescape things...
        return line.substring(1, line.length - 1)
    }
}