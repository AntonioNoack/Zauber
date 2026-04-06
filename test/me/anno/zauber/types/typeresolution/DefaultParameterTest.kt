package me.anno.zauber.types.typeresolution

import me.anno.zauber.types.Types
import me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefaultParameterTest {

    @Test
    fun testDefaultParameterWithoutSelf() {
        assertEquals(
            Types.Float,
            testTypeResolution(
                """
                fun call(x: Int = 0): Float
                
                val tested = call()
            """.trimIndent()
            )
        )
    }

    @Test
    fun testDefaultParameterWithSelf() {
        assertEquals(
            Types.Float,
            testTypeResolution(
                """
                class X {
                    fun call(x: Int = 0): Float
                }
                
                val tested = X().call()
            """.trimIndent()
            )
        )
    }

    @Test
    fun testDefaultParameterWithSelfExtension() {
        assertEquals(
            Types.Float,
            testTypeResolution(
                """
                class X
                fun X.call(x: Int = 0): Float
                
                val tested = X().call()
            """.trimIndent()
            )
        )
    }

    @Test
    fun testDefaultConstructorParam() {
        assertEquals(
            Types.Float,
            testTypeResolution(
                """
                class X(val x: Int = 0) {
                    fun call(): Float
                }
                
                val tested = X().call()
            """.trimIndent()
            )
        )
    }
}