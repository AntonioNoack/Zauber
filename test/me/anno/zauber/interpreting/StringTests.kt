package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.RuntimeCast.castToString
import me.anno.zauber.interpreting.TestRuntime.Companion.testExecute
import me.anno.zauber.logging.LogManager
import me.anno.zauber.types.Types.StringType
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
            
            // todo it might be better to make this 'external' for performance
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
        
        class Byte {}
        class Char {}
        class Int {
            external operator fun plus(other: Int): Int
            external operator fun minus(other: Int): Int
            external operator fun compareTo(other: Int): Int
            operator fun until(other: Int): IntRange = IntRange(this, other+1)
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
        
            val size get() = content.size
            val length get() = content.size
        
            constructor(str: String, startIndex: Int, endIndex: Int):
                this(str.content.copyOfRange(startIndex, endIndex))
                
            fun trim(): String {
                var startIndex = 0
                while (startIndex < size && this[startIndex] in whitespace) startIndex++
                if (startIndex == size) return this
                
                var endIndex = size
                while (endIndex > startIndex && this[endIndex] in whitespace) endIndex--
                return String(this, startIndex, endIndex)
            }
            
            operator fun get(index: Int): Char {
                return content[index].toChar()
            }
            
            operator fun plus(other: String): String {
                return String(content + other.content)
            }
        }
    """.trimIndent()

    @Test
    fun testStringField() {
        val code = """
            val tested get() = "Some String"
        """.trimIndent()
        val (rt, value) = testExecute(code)
        assertEquals(StringType, value.type.type)
        assertEquals("Some String", rt.castToString(value))
    }

    @Test
    fun testStringConcat() {
        val code = """
            val tested get() = "Some " + "String"
            
            package zauber
            class String {
                external operator fun plus(other: String) : String
            }
        """.trimIndent()
        val (rt, value) = testExecute(code)
        assertEquals(StringType, value.type.type)
        assertEquals("Some String", rt.castToString(value))
    }

    @Test
    fun testStringConcatUsingArrays() {
        LogManager.getLogger("Runtime").isDebugEnabled = true
        LogManager.getLogger("AssignmentExpression").isDebugEnabled = true
        val code = """
            val tested get() = "Some " + "String"
            
            package zauber
            $smallStdlib
        """.trimIndent()
        val (rt, value) = testExecute(code)
        assertEquals(StringType, value.type.type)
        assertEquals("Some String", rt.castToString(value))
    }
}