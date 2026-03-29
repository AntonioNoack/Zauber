package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.RuntimeCast.castToInt
import me.anno.zauber.types.Types.IntType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InheritanceInterfaceTests {

    @Test
    fun testCallInterfaceDefaultMethod() {
        val code = """
            interface Parent {
                open fun x() = 0
            }
            class Child: Parent
            fun runTest(p: Parent): Int = p.x()
            val tested = runTest(Child())
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(IntType, value.type.type)
        assertEquals(0, runtime.castToInt(value))
    }

    @Test
    fun testCallInterfaceDefaultField() {
        val code = """
            interface Parent {
                val x get() = 0
            }
            class Child: Parent
            fun runTest(p: Parent): Int = p.x
            val tested = runTest(Child())
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(IntType, value.type.type)
        assertEquals(0, runtime.castToInt(value))
    }

    @Test
    fun testCallInterfaceMethod() {
        val code = """
            interface Parent {
                fun x() = 0
            }
            class Child: Parent {
                override fun x() = 1
            }
            fun runTest(p: Parent): Int = p.x()
            val tested = runTest(Child())
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(IntType, value.type.type)
        assertEquals(1, runtime.castToInt(value))
    }

    @Test
    fun testCallInterfaceField() {
        val code = """
            interface Parent {
                val x get() = 0
            }
            class Child: Parent {
                override val x get() = 1
            }
            fun runTest(p: Parent): Int = p.x
            val tested = runTest(Child())
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(IntType, value.type.type)
        assertEquals(1, runtime.castToInt(value))
    }
}