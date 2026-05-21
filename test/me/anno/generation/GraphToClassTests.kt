package me.anno.generation

import me.anno.utils.assertEquals
import org.junit.jupiter.api.Test

class GraphToClassTests {
    @Test
    fun testGraphToClass() {
        val code = """
        fun fib(i: Int): Int {
            if (i <= 2) return i
            val v = Array<Int>(i+1)
            v[0] = 1
            v[1] = 1
            var j = 2
            while (j <= i) {
                v[j] = v[j-1] + v[j-2]
                j++
            }
            return v[i]
        }
        fun main() {
            println(fib(7))
        }
        package zauber
        class Any
        external class Int(val content: Int) {
            external operator fun plus(other: Int): Int
            external operator fun minus(other: Int): Int
            external operator fun compareTo(other: Int): Int
            operator fun inc(): Int = this + 1
        }
        enum class Boolean { TRUE, FALSE }
        class Array<V>(val size: Int) {
            external operator fun get(index: Int): V
            external operator fun set(index: Int, value: V)
        }
        external fun println(arg0: Int)
        """.trimIndent()

        // 1, 1, 2, 3, 5, 8, 13, 21
        val printed = WASMRuntimeTests().generator()
            .testCompileMainAndRun(code) {}
        assertEquals("21\n", printed)
    }
}