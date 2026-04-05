package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.RuntimeCast.castToInt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// todo most relevant topics:
//  lambda-capturing fields, var->Wrapper vs val->ConstructorParam
//  yielding
//  Java code gen for good real performance
//  Python parser

class InheritanceClassTests {

    @Test
    fun testCallChildMethod() {
        val code = """
            open class Parent {
                open fun x() = 0
            }
            class Child: Parent() {
                override fun x() = 1
            }
            fun runTest(p: Parent): Int = p.x()
            val tested = runTest(Child())
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(1, castToInt(value))
    }

    @Test
    fun testCallChildField() {
        val code = """
            open class Parent {
                open val x get() = 0
            }
            class Child: Parent() {
                override val x get() = 1
            }
            fun runTest(p: Parent): Int = p.x
            val tested = runTest(Child())
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(1, castToInt(value))
    }

    @Test
    fun testCallParentMethod() {
        val code = """
            open class Parent {
                open fun x() = 0
            }
            class Child: Parent() {
                override fun x() = 1
            }
            fun runTest(p: Parent): Int = p.x()
            val tested = runTest(Parent())
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(0, castToInt(value))
    }

    @Test
    fun testCallParentField() {
        val code = """
            open class Parent {
                open val x get() = 0
            }
            class Child: Parent() {
                override val x get() = 1
            }
            fun runTest(p: Parent): Int = p.x
            val tested = runTest(Parent())
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(0, castToInt(value))
    }
}