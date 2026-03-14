package me.anno.zauber.types.typeresolution

import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FieldTest {

    @Test
    fun testTypeByAssignment() {
        assertEquals(IntType, testTypeResolution("val tested = 0"))
    }

    @Test
    fun testTypeByDeclaration() {
        assertEquals(IntType, testTypeResolution("val tested: Int"))
    }

    @Test
    fun testTypeByGetter() {
        assertEquals(
            IntType,
            testTypeResolution(
                """
                val tested
                    get() = 0
            """.trimIndent()
            )
        )
    }

    // todo by delegate, when we have them

}