package me.anno.zauber.types.typeresolution

import me.anno.utils.ResolutionUtils.testTypeResolution
import me.anno.zauber.types.Types
import me.anno.utils.assertEquals
import org.junit.jupiter.api.Test

class VarargTest {

    private val floatArrayType
        get() = Types.Array.withTypeParameter(Types.Float)

    @Test
    fun testVarargWithNoParameters() {
        val actual = testTypeResolution(
            """
                fun <V> arrayOf(vararg values: V): Array<V>
                val tested = arrayOf<Float>()
                
                package zauber
            """.trimIndent()
        )
        assertEquals(floatArrayType, actual)
    }

    @Test
    fun testVarargWithNoParametersAndHint() {
        val actual = testTypeResolution(
            """
                fun <V> arrayOf(vararg values: V): Array<V>
                val tested: Array<Float> = arrayOf>()
                
                package zauber
            """.trimIndent()
        )
        assertEquals(floatArrayType, actual)
    }

    @Test
    fun testVarargWithOneParameter() {
        val actual = testTypeResolution(
            """
                fun <V> arrayOf(vararg values: V): Array<V>
                val tested = arrayOf(0f)
            """.trimIndent()
        )
        assertEquals(floatArrayType, actual)
    }

    @Test
    fun testVarargWithSomeParameters() {
        val actual = testTypeResolution(
            """
                fun <V> arrayOf(vararg values: V): Array<V>
                val tested = arrayOf(0f,1f,2f,43f)
            """.trimIndent()
        )
        assertEquals(floatArrayType, actual)
    }

}