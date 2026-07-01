package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.logging.LogManager
import me.anno.utils.assertEquals
import org.junit.jupiter.api.Test

class StringTests {

    val smallStdlib = "\n" + """
        package zauber
        
        class Byte {
            external fun toChar(): Char
        }
        class Char {
            external operator fun compareTo(other: Char): Int
            operator fun equals(other: Char): Boolean = this >= other && this <= other
        }
        
        fun min(a: Int, b: Int): Int = if (a < b) a else b
        
        class String(private val content: ByteArray) {
            companion object {
                val whitespace = " \t\r\n"
            }
        
            val size: Int get() = content.size
            val length: Int get() = content.size
        
            constructor(str: String, startIndex: Int, endIndex: Int):
                this(str.content.copyOfRange(startIndex, endIndex))
            
            operator fun get(index: Int): Char {
                return content[index].toChar()
            }
            
            operator fun plus(other: String): String {
                return String(content + other.content)
            }
            
            fun contains(char: Char): Boolean = indexOf(char) >= 0
            fun indexOf(char: Char, startIndex: Int = 0): Int {
                for (i in startIndex until length) {
                    if (this[i] == char) return i
                }
                return -1
            }
        }
    """.trimIndent()

    @Test
    fun testStringField() {
        val code = "val tested = \"Some String\""
        val value = testExecute(code)
        assertEquals("Some String", value.castToString())
    }

    @Test
    fun testStringConcat() {
        val code = """
            val tested = "Some " + "String"
            
            package zauber
            class String {
                external operator fun plus(other: String) : String
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals("Some String", value.castToString())
    }

    @Test
    fun testStringConcatUsingArrays() {
        val code = """
            val tested = "Some " + "String"
        """.trimIndent() + smallStdlib
        val value = testExecute(code)
        assertEquals("Some String", value.castToString())
    }

    @Test
    fun testStringTrimUsingArrays() {
        val code = """
            val tested = " Hello  \r\n".trim()
        """.trimIndent() + smallStdlib + """
            
            fun Char.isWhitespace(): Boolean = this in " \t\r\n"
            fun String.trim(): String {
                var startIndex = 0
                while (startIndex < length && this[startIndex].isWhitespace()) {
                    startIndex++
                }
                if (startIndex == length) return ""
                var endIndex = length - 1
                while (endIndex > startIndex && this[endIndex].isWhitespace()) {
                    endIndex--
                }
                return substring(startIndex, endIndex+1)
            }
            
            fun String.substring(startIndex: Int, endIndex: Int): String {
                val content = content.copyOfRange(startIndex, endIndex)
                return String(content)
            }
            
            fun <V> Array<V>.copyOfRange(startIndex: Int, endIndex: Int): Array<V> {
                val clone = Array<V>(endIndex-startIndex)
                copyInto(clone, 0, startIndex, endIndex)
                return clone
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals("Hello", value.castToString())
    }
}