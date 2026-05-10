package me.anno.zauber.types.typeresolution

import me.anno.zauber.types.Types
import me.anno.utils.ResolutionUtils.testTypeResolution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VarargTest {

    private val arrayOfFloatType
        get() = Types.Array.withTypeParameter(Types.Float)

    @Test
    fun testVarargWithNoParameters() {
        assertEquals(
            arrayOfFloatType,
            testTypeResolution(
                """
                fun <V> arrayOf(vararg values: V): Array<V>
                val tested = arrayOf<Float>()
                
                package zauber
            """.trimIndent()
            )
        )
    }

    @Test
    fun testVarargWithOneParameter() {
        assertEquals(
            arrayOfFloatType,
            testTypeResolution(
                """
                fun <V> arrayOf(vararg values: V): Array<V>
                val tested = arrayOf(0f)
            """.trimIndent()
            )
        )
    }

    @Test
    fun testVarargWithSomeParameters() {
        assertEquals(
            arrayOfFloatType,
            testTypeResolution(
                """
                fun <V> arrayOf(vararg values: V): Array<V>
                val tested = arrayOf(0f,1f,2f,43f)
            """.trimIndent()
            )
        )
    }

}