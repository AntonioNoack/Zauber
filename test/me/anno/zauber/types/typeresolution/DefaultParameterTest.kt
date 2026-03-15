package me.anno.zauber.types.typeresolution

import me.anno.zauber.types.StandardTypes
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DefaultParameterTest {

    @BeforeEach
    fun init() {
        // ensure it's loaded
        StandardTypes.standardClasses
    }

    @Test
    fun testDefaultParameterWithoutSelf() {
        assertEquals(
            FloatType,
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
            FloatType,
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
            FloatType,
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
            FloatType,
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