package me.anno.zauber.types.typeresolution

import me.anno.zauber.types.Types.IntType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PackageScopeTest {
    @Test
    fun testPackageScopeField() {
        assertEquals(
            IntType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution(
                """
                val value = 0
                val tested = value
            """.trimIndent()
            )
        )
    }

    @Test
    fun testPackageScopeMethod() {
        assertEquals(
            IntType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution(
                """
                fun method() = 0
                val tested = method()
            """.trimIndent()
            )
        )
    }

}