package me.anno.zauber.types.typeresolution

import me.anno.utils.ResolutionUtils.testTypeResolution
import me.anno.zauber.types.Types
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
        val type = testTypeResolution(
            """
                class X(val x: Int = 0) {
                    fun call(): Float
                }
                
                val tested = X().call()
            """.trimIndent()
        )
        assertEquals(Types.Float, type)
    }
}