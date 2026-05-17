package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.logging.LogManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CaptureTests {
    @Test
    fun testFieldCapture() {
        val code = """
            val tested: Int get() {
                var x = 1
                val inc = { x++ }
                inc()
                inc()
                return x
            }
            
            package zauber
            class Any
            external class Int {
                external operator fun plus(other: Int): Int
            }
            fun interface Function0<R> {
                fun call(): R
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }

    @Test
    fun testModifyingGlobalState() {
        // not to be captured...
        val code = """
            var x = 0
            fun f(): Int {
                x++
                if (x < 3) return f()
                return x
            }
            val tested = f()
            
            package zauber
            class Any
            external class Int {
                external operator fun plus(other: Int): Int
                external operator fun compareTo(other: Int): Int
                operator fun inc(): Int = this + 1
            }
            enum class Boolean { TRUE, FALSE }
            class Array<V>(val size: Int) {
                external operator fun set(index: Int, value: V)
            }
       """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }
}