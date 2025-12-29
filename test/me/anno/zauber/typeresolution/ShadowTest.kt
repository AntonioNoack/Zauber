package me.anno.zauber.typeresolution

import me.anno.zauber.Compile.stdlib
import me.anno.zauber.types.Types.FloatType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShadowTest {
    @Test
    fun testShadowedFields() {
        assertEquals(
            FloatType,
            TypeResolutionTest.testTypeResolution(
                """
                fun main(x: Int): Float {
                    val x = x+1
                    return x+1f
                }
                
                operator fun Int.plus(other: Float): Float
                operator fun Int.plus(other: Int): Int
                
                val tested = main(0)
                
                // register classes
                package $stdlib
                class Int
                class Float
            """.trimIndent()
            )
        )
    }
}