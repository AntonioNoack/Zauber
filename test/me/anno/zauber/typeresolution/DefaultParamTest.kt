package me.anno.zauber.typeresolution

import me.anno.zauber.types.Types.FloatType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefaultParamTest {
    @Test
    fun testDefaultParamWithoutSelf() {
        assertEquals(
            FloatType,
            TypeResolutionTest.testTypeResolution(
                """
                fun call(x: Int = 0): Float { return 1f }
                
                val tested = call()
            """.trimIndent()
            )
        )
    }

    @Test
    fun testDefaultParamWithSelf() {
        assertEquals(
            FloatType,
            TypeResolutionTest.testTypeResolution(
                """
                class X {
                    fun call(x: Int = 0): Float { return 1f }
                }
                
                val tested = X().call()
            """.trimIndent()
            )
        )
    }

    @Test
    fun testDefaultParamWithSelfExtension() {
        assertEquals(
            FloatType,
            TypeResolutionTest.testTypeResolution(
                """
                class X
                fun X.call(x: Int = 0): Float { return 1f }
                
                val tested = X().call()
            """.trimIndent()
            )
        )
    }
}