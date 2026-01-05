package me.anno.zauber.typeresolution

import me.anno.zauber.types.Types.IntType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FieldTest {

    @Test
    fun testTypeByAssignment() {
        assertEquals(
            IntType,
            TypeResolutionTest.testTypeResolution(
                """
                val tested = 0
            """.trimIndent()
            )
        )
    }

    @Test
    fun testTypeByDeclaration() {
        assertEquals(
            IntType,
            TypeResolutionTest.testTypeResolution(
                """
                val tested: Int
            """.trimIndent()
            )
        )
    }

    @Test
    fun testTypeByGetter() {
        assertEquals(
            IntType,
            TypeResolutionTest.testTypeResolution(
                """
                val tested
                    get() = 0
            """.trimIndent()
            )
        )
    }

    // todo by delegate, when we have them

}