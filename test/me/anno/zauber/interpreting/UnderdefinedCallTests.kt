package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.RuntimeCast.castToInt
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf

class UnderdefinedCallTests {

    @Test
    fun testArrayOf() {
        val (rt, valueT) = testExecute(
            """
            val tested = arrayOf(1, 2, 3)
            
            package zauber
            class Array<V>(override val size: Int) {
                external fun set(index: Int, value: V)
            }
            fun <V> arrayOf(vararg vs: V): Array<V> = vs
        """.trimIndent()
        )
        val expectedType = rt.getClass(Types.ArrayType.withTypeParameter(Types.IntType))
        assertEquals(expectedType, valueT.type)
        val contents = valueT.rawValue
        assertInstanceOf<IntArray>(contents)
        assertEquals(listOf(1, 2, 3), contents.toList())
    }

    // todo "const" could be a "deep" value, aka fully immutable
    //  if definitely should be available as some sort of qualifier to protect parameters from mutation

    @Test
    fun testListOf() {
        val (rt, value) = testExecute(
            """
            val tested = listOf(1, 2, 3)
            
            package zauber
            interface List<V> {
                val size: Int
            }
            class Array<V>(override val size: Int): List<V> {
                external operator fun set(index: Int, value: V)
            }
            fun <V> listOf(vararg vs: V): List<V> = vs
            fun <V> arrayOf(vararg vs: V): Array<V> = vs
        """.trimIndent()
        )
        when (val contents = value.rawValue) {
            is IntArray -> {
                // todo it should be this one..., but it's the other one
                assertEquals(Types.ArrayType.withTypeParameter(Types.IntType), value.type.type)
                assertEquals(listOf(1, 2, 3), contents.toList())
            }
            is Array<*> -> {
                assertEquals(Types.ArrayType, value.type.type)
                assertEquals(listOf(1, 2, 3), contents.map { value -> rt.castToInt(value as Instance) })
            }
            else -> throw IllegalStateException("$value is incorrect")
        }
    }

}