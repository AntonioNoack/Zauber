package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.logging.LogManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExplicitSelfTests {

    @Test
    fun testExplicitSelfWithInline() {
        // check the call is properly inlined -> yes
        // todo check after inlining, we have access to A, because S = A
        // -> S is resolved to Any-scope, why is it not specialized?
        // -> now S is proper, todo why is it not finding x() in A?
        LogManager.disableLoggers("" +
                "CallExpression,ConstructorResolver," +
                "Inheritance,")
        val code = """
            class A(val y: Int) {
                fun x() = y
            }
            val tested = A(2).run { x() }
            
            package zauber
            class Any
            class Int {
                external fun plus(other: Int): Int
            }
            inline fun <S, R> S.run(runnable: S.() -> R): R {
                return runnable()
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(2, value.castToInt())
    }

    @Test
    fun testExplicitSelfWithoutInline() {
        val code = """
            class A(val y: Int) {
                fun x() = y
            }
            val tested = A(2).run { x() }
            
            package zauber
            class Int {
                external fun plus(other: Int): Int
            }
            fun <S, R> S.run(runnable: S.() -> R): R {
                return runnable()
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(2, value.castToInt())
    }
}