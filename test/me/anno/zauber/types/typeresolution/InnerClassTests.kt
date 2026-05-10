package me.anno.zauber.types.typeresolution

import me.anno.zauber.interpreting.FieldGetSetTest.Companion.assertThrowsMessage
import me.anno.zauber.types.Types
import me.anno.utils.ResolutionUtils.testTypeResolution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InnerClassTests {

    @Test
    fun testInnerClassCanAccessOuterClass() {
        val type = testTypeResolution(
            """
                class X {
                    var x = 0f
                    inner class I {
                        fun call() = x
                    }
                }
                
                val tested = X().I().call()
            """.trimIndent(), reset = true
        )
        assertEquals(Types.Float, type)
    }

    @Test
    fun testInnerClassConstructor() {
        // todo why does this test fail when used together with others???
        val type = testTypeResolution(
            """
                class X {
                    inner class I {
                        fun call(): Float
                    }
                }
                
                val tested = X().I().call()
            """.trimIndent(), reset = true
        )
        assertEquals(Types.Float, type)
    }

    @Test
    fun testInnerClassConstructorUnavailableWithoutInstance() {
        assertThrowsMessage<IllegalStateException>({
            // todo check message
        }) {
            testTypeResolution(
                """
                class X {
                    inner class I {
                        fun call(): Float
                    }
                }
                
                val tested = X.I().call()
            """.trimIndent(), reset = true
            )
        }
    }
}