package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.utils.assertEquals
import org.junit.jupiter.api.Test

class CaptureTests {

    @Test
    fun testMutableCapturedField() {
        val code = """
            val tested: Int get() {
                var x = 1
                val inc = { x++ }
                inc()
                inc()
                return x
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }

    @Test
    fun testImmutableCapturedField() {
        val code = """
            val tested: Int get() {
                var x = 1
                val inc = { x+1 }
                return inc()
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }

    @Test
    fun testModifyingGlobalState() {
        // not to be captured...
        val code = """
            var x = 0
            fun f(): Int {
                x++
                if (x < 3) return f()
                return x
            }
            val tested = f()
       """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }
}