package me.anno.zauber.types.typeresolution

import me.anno.zauber.types.Types.IntType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FieldTest {

    @Test
    fun testTypeByAssignment() {
        assertEquals(
            IntType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution(
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
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution(
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
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution(
                """
                val tested
                    get() = 0
            """.trimIndent()
            )
        )
    }

    // todo by delegate, when we have them

}