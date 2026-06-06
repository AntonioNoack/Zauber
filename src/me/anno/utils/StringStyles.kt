package me.anno.utils


/**
 * different colors and styles that can be applied to printing in the terminal
 *
 * https://en.wikipedia.org/wiki/ANSI_escape_code
 * */
object StringStyles {
    const val ESC_CHAR = '\u001B'
    const val ESC = "$ESC_CHAR["

    const val RESET = "${ESC}0m"

    // Styles
    const val BOLD = "${ESC}1m"
    const val THIN = "${ESC}2m"
    const val THIN_BOLD_OFF = "${ESC}22m"
    const val ITALIC = "${ESC}3m"
    const val ITALIC_OFF = "${ESC}23m"
    const val UNDERLINE = "${ESC}4m"
    const val UNDERLINE_OFF = "${ESC}24m"
    const val BLINK = "${ESC}5m"
    const val BLINK_OFF = "${ESC}25m"

    const val COLOR_OFF = "${ESC}39m"
    const val BG_COLOR_OFF = "${ESC}49m"

    /**
     * changes text and background color
     * */
    const val REVERSE = "${ESC}7m"
    const val REVERSE_OFF = "${ESC}27m"
    const val HIDDEN = "${ESC}8m"
    const val HIDDEN_OFF = "${ESC}28m"
    const val STRIKETHROUGH = "${ESC}9m"
    const val STRIKETHROUGH_OFF = "${ESC}29m"

    fun Int.r() = shr(16).and(0xff)
    fun Int.g() = shr(8).and(0xff)
    fun Int.b() = and(0xff)

    fun color(color: Int) = color(color.r(), color.g(), color.b())
    fun color(r: Int, g: Int, b: Int) = "${ESC}38;2;$r;$g;${b}m"

    fun bgColor(color: Int) = bgColor(color.r(), color.g(), color.b())
    fun bgColor(r: Int, g: Int, b: Int) = "${ESC}48;2;$r;$g;${b}m"

    fun style(text: CharSequence, vararg codes: String): String {
        return style(text.toString(), codes.joinToString(""))
    }

    fun style(text: String, vararg codes: String): String {
        if (codes.isEmpty()) return text
        return style(text, codes.joinToString(""))
    }

    fun style(text: CharSequence, code: String): String {
        return style(text.toString(), code)
    }

    @Deprecated("No style declared")
    fun style(text: String): String {
        return text
    }

    fun bold(text: String) = style(text, BOLD)

    fun style(text: String, code: String): String {
        if (code.isEmpty()) return text

        return buildString {
            append(code)
            if (RESET in text) {
                // fix nested styles
                append(text.replace(RESET, RESET + code))
            } else append(text)
            append(RESET)
        }
    }

    private val removeStyleRegex = Regex("\\u001B\\[[0-9;]*m")
    fun removeStyles(text: String): String {
        return text.replace(removeStyleRegex, "")
    }

    /**
     * keywords
     * */
    val ORANGE = color(0xff9000)

    val WHITE = color(0xffffff)

    /**
     * local variables, arrows for underlining words
     * */
    val YELLOW = color(0xdddd77)

    /**
     * for types and numbers
     * */
    val DARK_BLUE = color(0x85A0FF)

    /**
     * for dependency-resolution-types
     * */
    val MEDIUM_BLUE = color(0x61B4D4)

    /**
     * sometimes used for types, when BLUE is too dark;
     * or for file names
     * */
    val LIGHT_BLUE = color(0x99bbee)

    /**
     * for blocks, field names and strings
     * */
    val GREEN = color(0x66cc66)

    /**
     * for line positions;
     * slightly brighter than normal text
     * */
    val TEXT = color(0xd0d0d0)

    /**
     * for allocated types
     * */
    val LIGHT_ORANGE = color(0xFFCC79)

    /**
     * for errors, and ending blocks
     * */
    val RED = color(0xff5555)

}