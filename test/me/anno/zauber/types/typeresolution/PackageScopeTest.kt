package me.anno.zauber.types.typeresolution

import me.anno.utils.ResolutionUtils.testTypeResolution
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PackageScopeTest {
    @Test
    fun testPackageScopeField() {
        val actual = testTypeResolution(
            """
                val value = 0
                val tested = value
            """.trimIndent()
        )
        assertEquals(Types.Int, actual)
    }

    @Test
    fun testPackageScopeMethod() {
        val actual = testTypeResolution(
            """
                fun method() = 0
                val tested = method()
            """.trimIndent()
        )
        assertEquals(Types.Int, actual)
    }

}