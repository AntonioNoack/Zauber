package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.utils.assertEquals
import org.junit.jupiter.api.Test

/**
 * this tests lambda-style calls, where generics may have to be derived from lambda-internals
 * */
class LambdaTests {

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
    
    fun <V,R> Array<V>.map(map: (V) -> R): Array<R> {
        var i = 0
        var result = Array<V>(size)
        while (i < size) {
            result[i] = map(this[i])
            i++
        }
        return result
    }
    
    fun <V> Array<V>.filter(predicate: (V) -> Boolean): Array<V> {
        var count = 0
        var i = 0
        while (i < size) {
            if(predicate(this[i])) count++
            i++
        }
        val result = Array<V>(count)
        i = 0; count = 0
        while (i < size) {
            if (predicate(this[i])) {
                result[count++] = this[i]
            }
            i++
        }
        return result
    }
    
    external class Int {
        external operator fun plus(other: Int): Int
        external fun compareTo(other: Int): Int
        infix fun inc() = this + 1
    }
    class Any
    class Array<V>(val size: Int) {
        external operator fun get(index: Int): V
        external operator fun set(index: Int, value: V)
    }
    fun <V> arrayOf(vararg values: V): Array<V> = values
    fun interface Function1<P0, R> {
        fun call(p0: P0): R
    }
    fun interface Function2<P0, P1, R> {
        fun call(p0: P0, p1: P1): R
    }
    enum class Boolean { TRUE, FALSE }
    """.trimIndent()

    // todo tests with _ (unnamed/hidden) parameters

    @Test
    fun testSimpleArrayReduceWithLambda() {
        val code = "val tested = arrayOf(1, 2, 3).reduce { a: Int, b: Int -> a + b }\n$stdlib"
        assertEquals(6, testExecute(code).castToInt())
    }

    @Test
    fun testArrayReduceWithLambda() {
        val code = "val tested = arrayOf(1, 2, 3).reduce { a, b -> a + b }\n$stdlib"
        assertEquals(6, testExecute(code).castToInt())
    }

    @Test
    fun testArrayReduceWithUnnamedParameter() {
        val code = "val tested = arrayOf(1, 2, 3).reduce { _, b -> b }\n$stdlib"
        assertEquals(3, testExecute(code).castToInt())
    }

    @Test
    fun testArrayMap() {
        val code = "val tested = arrayOf(1, 2, 3).map { 1 + it }\n$stdlib"
        assertEquals(6, testExecute(code).castToInt())
    }

    @Test
    fun testFilterWithoutNamedField() {
        val code = "val helper = arrayOf(1, 2, 3).filter { it > 1 }\n" +
                "val tested = helper.reduce { a, b -> a + b }\n$stdlib"
        assertEquals(5, testExecute(code).castToInt())
    }

    @Test
    fun testChainedLambda() {
        val code = "val tested = arrayOf(1, 2, 3).filter { it > 1 }.reduce { a, b -> a + b }\n$stdlib"
        assertEquals(5, testExecute(code).castToInt())
    }

    @Test
    fun testNestedLambda0() {
        val code = "val tested = arrayOf(1, 2, 3)" +
                ".map { arrayOf(it, -it) }" +
                ".flatten()" +
                ".reduce { a, b -> a * b }\n$stdlib"
        assertEquals(-36, testExecute(code).castToInt())
    }

    @Test
    fun testNestedLambda1() {
        val code = "val tested = arrayOf(1, 2, 3)" +
                ".map { arrayOf(it, -it).filter { it > 0} }" +
                ".flatten()" +
                ".reduce { a, b -> a * b }\n$stdlib"
        assertEquals(6, testExecute(code).castToInt())
    }

    @Test
    fun testArrayReduceWithTypeMethod() {
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