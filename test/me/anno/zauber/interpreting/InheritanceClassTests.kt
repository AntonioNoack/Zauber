package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// todo most relevant topics:
//  lambda-capturing fields, var->Wrapper vs val->ConstructorParam
//  yielding
//  Python parser

class InheritanceClassTests {

    @Test
    fun testFieldInSuperClassIsAssigned() {
        val code = """
            class Parent(val y: Int)
            class Child(x: Int): Parent(x)
            val tested = Child(3).y
            
            package zauber
            class Any
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }

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
            
            package zauber
            class Any
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(1, value.castToInt())
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
            
            package zauber
            class Any
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(1, value.castToInt())
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
            
            package zauber
            class Any
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(0, value.castToInt())
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
            
            package zauber
            class Any
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(0, value.castToInt())
    }
}