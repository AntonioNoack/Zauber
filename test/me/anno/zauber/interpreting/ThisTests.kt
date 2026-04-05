package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.interpreting.RuntimeCast.castToInt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThisTests {

    @Test
    fun testSimpleField() {
        val code = """
            class A(val x: Int)
            val tested = A(1).x
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(1, castToInt(value))
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
        assertEquals(1, castToInt(value))
    }

    @Test
    fun testFieldInRunnable() {
        val code = """
            class A(val x: Int)
            val tested = A(2).run { x }
            
            package zauber
            class Int {
                external fun plus(other: Int): Int
            }
            inline fun <S, R> S.run(runnable: S.() -> R): R {
                return runnable()
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(2, castToInt(value))
    }

    @Test
    fun testMethodInRunnable() {
        val code = """
            class A(val x: Int) {
                fun x() = x
            }
            val tested = A(2).run { x() }
            
            package zauber
            class Int {
                external fun plus(other: Int): Int
            }
            inline fun <S, R> S.run(runnable: S.() -> R): R {
                return runnable()
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(2, castToInt(value))
    }

    @Test
    fun testFieldInRunnableWithShadowingInMethod() {
        val code = """
            class A(val x: Int) {
                fun add1(x: Int): Int = A(1).run { x } + x
            }
            val tested = A(2).add1(5)
            
            package zauber
            class Int {
                external fun plus(other: Int): Int
            }
            inline fun <S, R> S.run(runnable: S.() -> R): R {
                return runnable()
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, castToInt(value))
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
            class Int {
                external fun plus(other: Int): Int
            }
            inline fun <S, R> S.run(runnable: S.() -> R): R {
                return runnable()
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, castToInt(value))
    }

    @Test
    fun testFieldInRunnableWithShadowingInSelf() {
        val code = """
            class A(val x: Int) {
                fun add1(): Int = A(1).run { x } + x
            }
            val tested = A(2).add1()
            
            package zauber
            class Int {
                external fun plus(other: Int): Int
            }
            inline fun <S, R> S.run(runnable: S.() -> R): R {
                return runnable()
            }
            fun interface Function1<P0, R> {
                fun call(p0: P0): R
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, castToInt(value))
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
            class Int {
                external fun plus(other: Int): Int
            }
            inline fun <S, R> S.run(runnable: S.() -> R): R {
                return runnable()
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, castToInt(value))
    }

}