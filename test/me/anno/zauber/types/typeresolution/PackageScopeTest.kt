package me.anno.zauber.types.typeresolution

import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PackageScopeTest {
    @Test
    fun testPackageScopeField() {
        assertEquals(
            IntType,
            testTypeResolution(
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
            testTypeResolution(
                """
                fun method() = 0
                val tested = method()
            """.trimIndent()
            )
        )
    }

}