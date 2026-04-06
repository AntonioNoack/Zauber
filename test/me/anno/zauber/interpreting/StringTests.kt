package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.logging.LogManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StringTests {

    val smallStdlib = """
        class Array<V>(val size: Int) {
            external operator fun get(index: Int): V
            external operator fun set(index: Int, value: V)
            
            operator fun plus(other: Array<V>): Array<V> {
                val result = copyOf(size + other.size)
                other.copyInto(result, size, 0, other.size)
                return result
            }
            
            fun copyInto(result: Array<V>, destinationOffset: Int, startIndex: Int, endIndex: Int) {
                val deltaIndex = destinationOffset - startIndex
                for (i in startIndex until endIndex) {
                    result[deltaIndex + i] = this[i]
                }
            }
            
            fun copyOf(newSize: Int): Array<V> {
                val clone = Array<V>(newSize)
                copyInto(clone, 0, 0, min(newSize, size))
                return clone
            }
        }
        
        typealias ByteArray = Array<Byte>
        
        class Byte {
            external fun toChar(): Char
        }
        class Char {
            external operator fun compareTo(other: Char): Int
            operator fun equals(other: Char): Boolean = this >= other && this <= other
        }
        
        class Int {
            external operator fun plus(other: Int): Int
            external operator fun minus(other: Int): Int
            external operator fun compareTo(other: Int): Int
            operator fun until(other: Int): IntRange = IntRange(this, other)
            operator fun equals(other: Int): Boolean = this >= other && this <= other
            operator fun unaryMinus(): Int = 0 - this
            fun inc() = this+1
            fun dec() = this-1
        }
        
        fun min(a: Int, b: Int): Int = if (a < b) a else b
        
        class IntRange(val from: Int, val to: Int) {
            fun iterator() = IntRangeIterator(this)
        }
        
        interface Iterator<V> {
            fun next(): Int
            fun hasNext(): Boolean
        }
        
        class IntRangeIterator(val range: IntRange): Iterator<Int> {
            var index = range.from
            override fun hasNext(): Boolean = index < range.to
            override fun next(): Int = index++
        }
        
        object Unit
        enum class Boolean {
            TRUE, FALSE
        }
        
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
        LogManager.disableLoggers(
            "Inheritance,Runtime,CallExpression,ConstructorResolver,MemberResolver," +
                    "ResolvedField,FieldResolver,FieldExpression,AssignmentExpression," +
                    "Stdlib,ASTSimplifier,ResolvedMethod"
        )
        val code = """
            val tested = "Some " + "String"
            
            package zauber
            $smallStdlib
        """.trimIndent()
        val value = testExecute(code)
        assertEquals("Some String", value.castToString())
    }

    @Test
    fun testStringTrimUsingArrays() {
        LogManager.disableLoggers(
            "Inheritance,Runtime,CallExpression,ConstructorResolver," +
                    "FieldExpression,AssignmentExpression,MethodResolver," +
                    "Stdlib,ASTSimplifier,ResolvedMethod"
        )
        val code = """
            val tested = " Hello  \r\n".trim()
            
            package zauber
            $smallStdlib
            
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
                return String(content.copyOfRange(startIndex, endIndex))   
            }
            
            fun <V> Array<V>.copyOfRange(startIndex: Int, endIndex: Int) {
                val clone = Array(endIndex-startIndex)
                copyInto(clone, 0, startIndex, endIndex)
                return clone
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals("Hello", value.castToString())
    }
}