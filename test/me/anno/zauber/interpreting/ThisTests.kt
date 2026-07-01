package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.logging.LogManager
import me.anno.utils.assertEquals
import org.junit.jupiter.api.Test

class ThisTests {

    @Test
    fun testSimpleField() {
        val code = """
            class A(val x: Int)
            val tested = A(1).x
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(1, value.castToInt())
    }

    @Test
    fun testSimpleMethod() {
        val code = """
            class A(val x: Int) {
                fun x() = x
            }
            val tested = A(1).x()
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(1, value.castToInt())
    }

    @Test
    fun testFieldInRunnable() {
        val code = """
            class A(val x: Int)
            val tested = A(2).run { x }
            
            package zauber
            inline fun <S, R> S.run(runnable: S.() -> R): R {
                return runnable()
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(2, value.castToInt())
    }

    @Test
    fun testMethodInRunnable() {
        val code = """
            class A(val x: Int) {
                fun x() = x
            }
            val tested = A(2).run { x() }
            
            package zauber
            inline fun <S, R> S.run(runnable: S.() -> R): R {
                return runnable()
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(2, value.castToInt())
    }

    @Test
    fun testFieldInRunnableWithShadowingInMethod() {
        val code = """
            class A(val x: Int) {
                fun add1(x: Int): Int = A(1).run { x } + x
            }
            val tested = A(2).add1(5)
            
            package zauber
            inline fun <S, R> S.run(runnable: S.() -> R): R {
                return runnable()
            }
            interface Function0<R> {
                fun call(): R
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }

    @Test
    fun testMethodInRunnableWithShadowingInMethod() {
        val code = """
            class A(val x: Int) {
                fun add1(x: Int): Int = A(1).run { x() } + x
                fun x() = x
            }
            val tested = A(2).add1(5)
            
            package zauber
            inline fun <S, R> S.run(runnable: S.() -> R): R {
                return runnable()
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }

    @Test
    fun testFieldInRunnableWithShadowingInSelf() {
        val code = """
            class A(val x: Int) {
                fun add1(): Int = A(1).run { x } + x
            }
            val tested = A(2).add1()
            
            package zauber
            inline fun <S, R> S.run(runnable: S.() -> R): R {
                return runnable()
            }
            fun interface Function0<R> {
                fun call(): R
            }
            fun interface Function1<P0, R> {
                fun call(p0: P0): R
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }

    @Test
    fun testMethodInRunnableWithShadowingInSelf() {
        val code = """
            class A(val x: Int) {
                fun add1(): Int = A(1).run { x() } + x()
                fun x() = x
            }
            val tested = A(2).add1()
            
            package zauber
            inline fun <S, R> S.run(runnable: S.() -> R): R {
                return runnable()
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }

    @Test
    fun testExplicitThisWithInnerClass() {
        // todo implement constructing inner classes from outside...
        val code = """
            class A(val x: Int) {
                inner class B(val x: Int) {
                    fun test() = this@A.x + this.x
                }
            }
            val tested = A(1).B(2).test()
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }


    @Test
    fun testIterateOverList() {
        val sourceCode = """
val tested: Int get() {
    val data = listOf(1,2,3)
    var sum = 0
    for (value in data) {
        sum += value
    }
    return sum
}
        """.trimIndent()
        assertEquals(6, testExecute(sourceCode).castToInt())
    }

}