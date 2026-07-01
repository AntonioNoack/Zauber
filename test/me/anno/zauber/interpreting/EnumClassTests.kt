package me.anno.zauber.interpreting

import me.anno.utils.MultiTest
import me.anno.utils.assertEquals
import me.anno.zauber.types.Types
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

// todo test enums have toString(), name and ordinal
// todo I don't think we have code for them yet, generate it

class EnumClassTests {

    private val stdlib = "\n" + """
package zauber
open class Any {
    open external fun equals(other: Any?): Boolean
    open external fun hashCode(): Int
}
enum class Boolean { TRUE, FALSE }
class Array<V>(val size: Int) {
    external operator fun set(index: Int, value: V)
}
external class Int {
    external operator fun plus(other: Int): Int
    external operator fun times(other: Int): Int
}
external fun println(arg0: Boolean)
external fun println(arg0: Int)
external fun println(arg0: String)
class String
    """.trimIndent()

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime", "wasm"])
    fun testEqualsMethod(type: String) {
        val code = """
            enum class E { A, B, C }
            val tested = E.A == E.A
        """.trimIndent() + stdlib
        MultiTest(code)
            .type { Types.Boolean }
            .runtime { value -> assertEquals(true, value.castToBool()) }
            .compile("true\n")
            .runTest(type)
    }

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime", "wasm"])
    fun testOrdinal(type: String) {
        val code = """
            enum class E { A, B, C, D }
            val tested = E.C.ordinal * E.D.ordinal
        """.trimIndent() + stdlib
        MultiTest(code)
            .type { Types.Boolean }
            .runtime { value -> assertEquals(6, value.castToInt()) }
            .compile("6\n")
            .runTest(type)
    }

    @ParameterizedTest
    @ValueSource(strings = ["type",/* "runtime", "wasm"*/])
    fun testToString(type: String) {
        val code = """
            enum class E { A, B, C, D }
            val tested = E.C.toString()
        """.trimIndent() + stdlib
        MultiTest(code)
            .type { Types.String }
            .runtime { value -> assertEquals("C", value.castToString()) }
            .compile("C\n")
            .runTest(type)
    }

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime", "wasm"])
    fun testHashCode(type: String) {
        val code = """
            enum class E { A, B, C, D }
            val tested = E.C.hashCode()
        """.trimIndent() + stdlib
        MultiTest(code)
            .type { Types.Int }
            .runtime { value -> assertEquals(2, value.castToInt()) }
            .compile("2\n")
            .runTest(type)
    }
}