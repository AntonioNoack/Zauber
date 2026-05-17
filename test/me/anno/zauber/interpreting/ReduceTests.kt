package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
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
    external class Int {
        operator fun plus(other: Int): Int
        operator fun plus(other: Float): Float
        external fun compareTo(other: Int): Int
        infix fun inc() = this + 1
    }
    class Any
    class Array<V>(val size: Int) {
        external operator fun get(index: Int): V
        external operator fun set(index: Int, value: V)
    }
    fun <V> arrayOf(vararg values: V): Array<V> = values
    fun interface Function2<P0, P1, R> {
        fun call(p0: P0, p1: P1): R
    }
    enum class Boolean { TRUE, FALSE }
    """.trimIndent()

    @Test
    fun testArrayReduceWithLambda() {
        val code = "val tested = arrayOf(1, 2, 3).reduce { a, b -> a + b }\n$stdlib"
        assertEquals(6, testExecute(code).castToInt())
    }

    @Test
    fun testSimpleArrayReduceWithLambda() {
        val code = "val tested = arrayOf(1, 2, 3).reduce { a: Int, b: Int -> a + b }\n$stdlib"
        assertEquals(6, testExecute(code).castToInt())
    }

    @Test
    fun testArrayReduceWithTypeMethod() {
        // todo why is the baseType Any instead of Int???
        val code = "val tested = arrayOf(1, 2, 3).reduce(Int::plus)\n$stdlib"
        assertEquals(6, testExecute(code).castToInt())
    }

    @Test
    fun testSavingMethodReferenceInField() {
        val code = """
            val tested: Int get() {
                val f = Int::plus
                return f(1, 2)
            }
            
            package zauber
            external class Int {
                external operator fun plus(other: Int): Int
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }
}