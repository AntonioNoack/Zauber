package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.utils.assertEquals
import org.junit.jupiter.api.Test

class GetterSetterTests {

    @Test
    fun testGetterIsExecutedWithoutBackingField() {
        val code = """
            val x get() = 17f
            val tested get() = x
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(17f, value.castToFloat())
    }

    @Test
    fun testGetterIsExecutedWithBackingFieldInPackage() {
        val code = """
            var x = 3
                get() = field++
                
            val tested get() = x * x
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(12, value.castToInt())
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
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(12, value.castToInt())
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
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(12, value.castToInt())
    }

}