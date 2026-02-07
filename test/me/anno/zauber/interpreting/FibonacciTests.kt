package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.RuntimeCast.castToInt
import me.anno.zauber.interpreting.TestRuntime.Companion.testExecute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FibonacciTests {

    private val stdlib = """
        package zauber
        object Unit
        class Int {
            external operator fun plus(other: Int): Int
            external operator fun minus(other: Int): Int
            external operator fun compareTo(other: Int): Int
            operator fun until(other: Int): IntRange = IntRange(this, other)
            operator fun rangeTo(other: Int): IntRange = IntRange(this, other+1)
            fun inc() = this+1
            fun dec() = this-1
        }
        
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
        
        enum class Boolean { TRUE, FALSE }
        class Array<V>(val size: Int) {
            external operator fun get(i: Int)
            external operator fun set(i: Int, value: V)
        }
        """.trimIndent()

    @Test
    fun testLoopFibonacci() {
        val code = """
        fun fib(i: Int): Int {
            var a = 1
            var b = 0
            for (k in 0 .. i) {
                val tmp = a
                a = a + b
                b = tmp
            }
            return b
        }
        val tested get() = fib(7)
        $stdlib
        """.trimIndent()
        // 1, 1, 2, 3, 5, 8, 13, 21
        val (runtime, value) = testExecute(code)
        assertEquals(21, runtime.castToInt(value))
    }

    @Test
    fun testRecursiveFibonacci() {
        val code = """
        fun fib(i: Int): Int {
            if (i < 2) return 1
            return fib(i-1) + fib(i-2)
        }
        val tested get() = fib(5)
        $stdlib
        """.trimIndent()
        // 1, 1, 2, 3, 5, 8
        val (runtime, value) = testExecute(code)
        assertEquals(8, runtime.castToInt(value))
    }

}