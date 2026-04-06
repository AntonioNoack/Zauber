package me.anno.zauber.types.typeresolution

import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes
import me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExprOrNullTest {

    @Test
    fun testExprOrNull() {
        assertEquals(
            unionTypes(Types.Float, NullType),
            testTypeResolution(
                """
                fun Int.plus(other: Float): Float
                
                val x: Int? = null
                val tested = x?.plus(0f)
                
                package zauber
                class Int
                class Float
            """.trimIndent()
            )
        )
    }

    @Test
    fun testExprOrOther() {
        assertEquals(
            unionTypes(Types.Float, Types.Double),
            testTypeResolution(
                """
                fun Int.plus(other: Float): Float
                
                val x: Int? = null
                val tested = x?.plus(0f) ?: 0.0
                
                package zauber
                class Int
                class Float
            """.trimIndent()
            )
        )
    }
}