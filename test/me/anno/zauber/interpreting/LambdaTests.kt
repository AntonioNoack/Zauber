package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.utils.assertEquals
import org.junit.jupiter.api.Test

/**
 * this tests lambda-style calls, where generics may have to be derived from lambda-internals
 * */
class LambdaTests {

    // todo tests with _ (unnamed/hidden) parameters

    @Test
    fun testSimpleArrayReduceWithLambda() {
        val code = "val tested = arrayOf(1, 2, 3).reduce { a: Int, b: Int -> a + b }"
        assertEquals(6, testExecute(code).castToInt())
    }

    @Test
    fun testArrayReduceWithLambda() {
        val code = "val tested = arrayOf(1, 2, 3).reduce { a, b -> a + b }"
        assertEquals(6, testExecute(code).castToInt())
    }

    @Test
    fun testArrayReduceWithUnnamedParameter() {
        val code = "val tested = arrayOf(1, 2, 3).reduce { _, b -> b }"
        assertEquals(3, testExecute(code).castToInt())
    }

    @Test
    fun testArrayMap() {
        val code = "val tested = arrayOf(1, 2, 3).map { 1 + it }"
        assertEquals(6, testExecute(code).castToInt())
    }

    @Test
    fun testFilterWithoutNamedField() {
        val code = "val helper = arrayOf(1, 2, 3).filter { it > 1 }\n" +
                "val tested = helper.reduce { a, b -> a + b }"
        assertEquals(5, testExecute(code).castToInt())
    }

    @Test
    fun testChainedLambda() {
        val code = "val tested = arrayOf(1, 2, 3).filter { it > 1 }.reduce { a, b -> a + b }"
        assertEquals(5, testExecute(code).castToInt())
    }

    @Test
    fun testNestedLambda0() {
        val code = "val tested = arrayOf(1, 2, 3)" +
                ".map { arrayOf(it, -it) }" +
                ".flatten()" +
                ".reduce { a, b -> a * b }"
        assertEquals(-36, testExecute(code).castToInt())
    }

    @Test
    fun testNestedLambda1() {
        val code = "val tested = arrayOf(1, 2, 3)" +
                ".map { arrayOf(it, -it).filter { it > 0} }" +
                ".flatten()" +
                ".reduce { a, b -> a * b }"
        assertEquals(6, testExecute(code).castToInt())
    }

    @Test
    fun testArrayReduceWithTypeMethod() {
        val code = "val tested = arrayOf(1, 2, 3).reduce(Int::plus)"
        assertEquals(6, testExecute(code).castToInt())
    }

    @Test
    fun testSavingMethodReferenceInField() {
        val code = """
            val tested: Int get() {
                val f = Int::plus
                return f(1, 2)
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }

}