package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.RuntimeCast.castToFloat
import me.anno.zauber.interpreting.TestRuntime.Companion.testExecute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetterSetterTests {

    @Test
    fun testGetterIsExecutedWithoutBackingField() {
        val code = """
            val x get() = 17f
            val tested get() = x
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(17f, runtime.castToFloat(value))
    }

    @Test
    fun testGetterIsExecutedWithBackingFieldInPackage() {
        val code = """
            var x = 3
                get() = field++
                
            val tested get() = x * x
            
            package zauber
            class Int {
                external operator fun plus(other: Int): Int
                external operator fun times(other: Int): Int
                fun inc() = this + 1
            }
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(12, runtime.castToFloat(value))
    }

    @Test
    fun testGetterIsExecutedWithBackingFieldInClass() {
        val code = """
            class Wrapper {
                var x = 3
                    get() = field++
            }
                
            val tested: Int get() {
                val w = Wrapper()
                return w.x * w.x
            }
            
            package zauber
            class Int {
                external operator fun plus(other: Int): Int
                external operator fun times(other: Int): Int
                fun inc() = this + 1
            }
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(12, runtime.castToFloat(value))
    }

    @Test
    fun testGetterIsExecutedWithBackingFieldInObject() {
        val code = """
            object Wrapper {
                var x = 3
                    get() = field++
            }
                
            val tested: Int get() {
                return Wrapper.x * Wrapper.x
            }
            
            package zauber
            class Int {
                external operator fun plus(other: Int): Int
                external operator fun times(other: Int): Int
                fun inc() = this + 1
            }
            object Unit
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(12, runtime.castToFloat(value))
    }

}