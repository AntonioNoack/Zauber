package me.anno.zauber.interpreting

import me.anno.utils.MultiTest
import me.anno.utils.assertEquals
import me.anno.zauber.types.Types
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

// todo test them all on value classes, too

class DataClassTests {

    private val stdlib = "\n" + """
package zauber
class Any
enum class Boolean { TRUE, FALSE }
class Array<V>(val size: Int) {
    external operator fun set(index: Int, value: V)
}
external fun println(arg0: Boolean)
external fun println(arg0: Int)
external fun println(arg0: String)
class String
    """.trimIndent()

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime", "wasm"])
    fun testEqualsMethodAny(type: String) {
        val code = """
            data class Vector(val x: Int, val y: Int)
            val tested = Vector(1, 2) == Vector(1, 2)
        """.trimIndent() + stdlib
        MultiTest(code)
            .type { Types.Boolean }
            .runtime { value -> assertEquals(true, value.castToBool()) }
            .compile("true\n")
            .runTest(type)
    }

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime", "wasm"])
    fun testHashCodeMethod(type: String) {
        val code = """
            data class Vector(val x: Int, val y: Int)
            val tested = Vector(1, 2).hashCode()
        """.trimIndent() + stdlib
        MultiTest(code)
            .type { Types.Int }
            .runtime { value -> assertEquals(1 * 31 + 2, value.castToInt()) }
            .compile("${1 * 31 + 2}\n")
            .runTest(type)
    }

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime"])
    fun testToStringMethod(type: String) {
        val code = """
            data class Vector(val x: Int, val y: Int)
            val tested = Vector(1, 2).toString()
        """.trimIndent() + stdlib
        MultiTest(code)
            .type { Types.String }
            .runtime { value -> assertEquals("Vector(x=1, y=2)", value.castToString()) }
            .compile("Vector(x=1, y=2)\n")
            .runTest(type)
    }

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime", "wasm"])
    fun testCopyMethodWithOneValue(type: String) {
        val code = """
            data class Vector(val x: Int, val y: Int)
            val tested = Vector(1, 2).copy(y=3).y
        """.trimIndent() + stdlib
        MultiTest(code)
            .type { Types.Int }
            .runtime { value -> assertEquals(3, value.castToInt()) }
            .compile("3\n")
            .runTest(type)
    }

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime", "wasm"])
    fun testCopyMethodWithMultipleValues(type: String) {
        val code = """
            data class Vector(val x: Int, val y: Int)
            val tested = Vector(1, 2).copy(x=2,y=3).y
        """.trimIndent() + stdlib
        MultiTest(code)
            .type { Types.Int }
            .runtime { value -> assertEquals(3, value.castToInt()) }
            .compile("3\n")
            .runTest(type)
    }
}