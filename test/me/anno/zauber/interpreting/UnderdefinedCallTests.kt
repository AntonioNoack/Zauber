package me.anno.zauber.interpreting

import me.anno.MultiTest
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class UnderdefinedCallTests {

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime", "js", "java", "c++", "wasm"])
    fun testArrayOf(type: String) {
        val code = """
            val tested = arrayOf(1, 2, 3)
            
            fun main() {
                println(tested[1])
            }
            
            package zauber
            class Any
            external class Int
            class Array<V>(override val size: Int) {
                external fun set(index: Int, value: V)
                external fun get(index: Int): V
            }
            fun <V> arrayOf(vararg vs: V): Array<V> = vs
            external fun println(arg0: Int)
        """.trimIndent()

        MultiTest(code)
            .type { Types.Array.withTypeParameter(Types.Int) }
            .runtime {
                val valueT = testExecute(code)
                val expectedType = runtime.getClass(Types.Array.withTypeParameter(Types.Int))
                assertEquals(expectedType, valueT.clazz)
                val contents = valueT.rawValue
                assertInstanceOf<IntArray>(contents)
                assertEquals(listOf(1, 2, 3), contents.toList())
            }
            .compile("1\n")
            .runTest(type)
    }

    // can we enforce const to be fully immutable?
    //  we could only allow it on value-classes of value-classes...

    @Test
    fun testListOf() {
        val value = testExecute(
            """
            val tested = listOf(1, 2, 3)
            
            package zauber
            class Any
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
                assertEquals(Types.Array.withTypeParameter(Types.Int), value.clazz.type)
                assertEquals(listOf(1, 2, 3), contents.toList())
            }
            is Array<*> -> {
                assertEquals(Types.Array, value.clazz.type)
                assertEquals(listOf(1, 2, 3), contents.map { value -> (value as Instance).castToInt() })
            }
            else -> throw IllegalStateException("$value is incorrect")
        }
    }

    private val stdlib = """
    package zauber
    fun <V> Array<V>.reduce(map: (V, V) -> V): V {
        var i = 1
        var result = this[0]
        while (i < size) {
            result = map(result, this[i])
            i++
        }
        return result
    }
    external class Int {
        operator fun plus(other: Int): Int
        operator fun plus(other: Float): Float
        external fun compareTo(other: Int): Int
        infix fun inc() = this + 1
    }
    class Any
    class Array<V>(val size: Int) {
        external operator fun get(index: Int): V
        external operator fun set(index: Int, value: V)
    }
    fun <V> arrayOf(vararg values: V): Array<V> = values
    fun interface Function2<P0, P1, R> {
        fun call(p0: P0, p1: P1): R
    }
    enum class Boolean { TRUE, FALSE }
    """.trimIndent()

    @Test
    fun testArrayReduceWithLambda() {
        val code = "val tested = arrayOf(1, 2, 3).reduce { a, b -> a + b }\n$stdlib"
        assertEquals(6, testExecute(code).castToInt())
    }

    @Test
    fun testSimpleArrayReduceWithLambda() {
        val code = "val tested = arrayOf(1, 2, 3).reduce { a: Int, b: Int -> a + b }\n$stdlib"
        assertEquals(6, testExecute(code).castToInt())
    }

    @Test
    fun testArrayReduceWithTypeMethod() {
        // todo why is the baseType Any instead of Int???
        val code = "val tested = arrayOf(1, 2, 3).reduce(Int::plus)\n$stdlib"
        assertEquals(6, testExecute(code).castToInt())
    }

    @Test
    fun testSavingMethodReferenceInField() {
        val code = """
            val tested: Int get() {
                val f = Int::plus
                return f(1, 2)
            }
            
            package zauber
            external class Int {
                external operator fun plus(other: Int): Int
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }

}