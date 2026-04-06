package me.anno.zauber.types.typeresolution

import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes
import me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClassCastTest {
    @Test
    fun testTypeIsCastInBranch() {
        assertEquals(
            unionTypes(Types.Float, Types.String),
            testTypeResolution(
                """
                fun Int.plus(other: Float): Float
                
                val x: Int? = null
                val tested = if(x == null) "Test" else x+1f
                
                // ensure these are registered as classes
                package zauber
                class Int
                class Float
                class String
            """.trimIndent()
            )
        )
    }

    @Test
    fun testTypeIsCastAfterReturningBranch() {
        // todo can we somehow test this?? we need to resolve the x+1f inside the getter...
        assertEquals(
            unionTypes(Types.Float, NullType),
            testTypeResolution(
                """
                fun Int.plus(other: Float): Float
                
                val x: Int? = null
                val tested: Float? get() {
                    if (x == null) return null
                    return x+1f
                }
            """.trimIndent()
            )
        )
        check(false) { "We need to test the result/mechanics" }
    }
}