package me.anno.zauber.typeresolution

import me.anno.zauber.types.StandardTypes
import me.anno.zauber.types.Types.FloatType
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
            TypeResolutionTest.testTypeResolution(
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
            TypeResolutionTest.testTypeResolution(
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
            TypeResolutionTest.testTypeResolution(
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
            TypeResolutionTest.testTypeResolution(
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