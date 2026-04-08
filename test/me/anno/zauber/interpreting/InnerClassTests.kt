package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InnerClassTests {

    @Test
    fun testInnerClassCanAccessOuterClass() {
        // todo in inner classes
        //  we need to add a synthetic field for the other class,
        //  and we must also add it to all constructors, like in enums
        val value = testExecute(
            """
                class X {
                    var x = 0f
                    inner class I {
                        fun call() = x
                    }
                }
                
                val tested = X().I().call()
            """.trimIndent()
        )
        assertEquals(0f, value.castToFloat())
    }

    @Test
    fun testInnerClassConstructor() {
        val type = testExecute(
            """
                class X {
                    inner class I {
                        fun call(): Float = 0f
                    }
                }
                
                val tested = X().I().call()
            """.trimIndent()
        )
        assertEquals(0f, type.castToFloat())
    }
}