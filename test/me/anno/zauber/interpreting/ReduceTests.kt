package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.RuntimeCast.castToInt
import me.anno.zauber.interpreting.TestRuntime.Companion.testExecute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReduceTests {

    private val stdlib = """
    package zauber
    fun <V> Array<V>.reduce(map: (V, V) -> V): V {
        var i = 1
        var result = this[0]
        while (i < size) {
            result = map(result, this[i])
            i++
        }
        return result
    }
    class Int {
        operator fun plus(other: Int): Int
        operator fun plus(other: Float): Float
    }
    class Any
    class Array<V>(val size: Int) {
        external operator fun get(index: Int): V
        external operator fun set(index: Int, value: V)
    }
    fun <V> arrayOf(vararg values: V): Array<V> = values
    """.trimIndent()

    @Test
    fun testArrayReduceWithLambda() {
        val code = """
                val tested get() = arrayOf(1, 2, 3).reduce { a, b -> a + b }
                $stdlib
            """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(6, runtime.castToInt(value))
    }

    @Test
    fun testArrayReduceWithTypeMethod() {
        val code = """
                val tested get() = arrayOf(1, 2, 3).reduce(Int::plus)
                $stdlib
            """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(6, runtime.castToInt(value))
    }
}