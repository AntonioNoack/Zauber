package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LoopTests {
    @Test
    fun testWhileWithoutBody() {
        val code = """
            fun call(): Int {
                var x = 1
                while (++x < 16);
                return x
            }
            
            val tested = call()
            
            package zauber
            class Any
            object Unit
            class Int {
                external operator fun plus(other: Int): Int
                external operator fun compareTo(other: Int): Int
                operator fun inc() = this + 1
            }
            
            enum class Boolean { TRUE, FALSE }
            class Array<V>(val size: Int) {
                external fun set(index: Int, value: V)
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(16, value.castToInt())
    }

    @Test
    fun testDoWhileWithoutBody() {
        val code = """
            fun call(): Int {
                var x = 1
                do; while(++x < 16);
                return x
            }
            
            val tested = call()
            
            package zauber
            class Any
            object Unit
            class Int {
                external operator fun plus(other: Int): Int
                external operator fun compareTo(other: Int): Int
                operator fun inc() = this + 1
            }
            
            enum class Boolean { TRUE, FALSE }
            class Array<V>(val size: Int) {
                external fun set(index: Int, value: V)
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(16, value.castToInt())
    }

    @Test
    fun testForWithoutBody() {
        // how can we test it progressed? global counter
        // todo why does it have an issue with Unit???
        val code = """
            fun call(): Int {
                for(k in 0 until 16);
                return zauber.State.state
            }
            
            val tested = call()
            
            package zauber
            class Any
            object Unit
            class Int {
                external operator fun plus(other: Int): Int
                external operator fun compareTo(other: Int): Int
                operator fun until(other: Int) = IntRange(this, other)
            }
            
            object State {
                var state = 0
            }
            
            // mutable int-range-iterator for testing
            class IntRange(var start: Int, val end: Int) {
                fun iterator() = this
                fun hasNext() = start < end
                fun next(): Int {
                    val value = start
                    start = value + 1
                    State.state = start
                    return value
                }
            }
            
            enum class Boolean { TRUE, FALSE }
            class Array<V>(val size: Int) {
                external fun set(index: Int, value: V)
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(16, value.castToInt())
    }
}