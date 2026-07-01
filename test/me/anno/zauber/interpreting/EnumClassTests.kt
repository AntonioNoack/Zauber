package me.anno.zauber.interpreting

import me.anno.utils.MultiTest
import me.anno.utils.assertEquals
import me.anno.zauber.types.Types
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

// todo test enums have toString(), name and ordinal
// todo I don't think we have code for them yet, generate it

class EnumClassTests {

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime", "wasm"])
    fun testEqualsMethod(type: String) {
        val code = """
            enum class E { A, B, C }
            val tested = E.A == E.A
        """.trimIndent()
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
        """.trimIndent()
        MultiTest(code)
            .type { Types.Boolean }
            .runtime { value -> assertEquals(6, value.castToInt()) }
            .compile("6\n")
            .runTest(type)
    }

    @ParameterizedTest
    @ValueSource(strings = ["type"/* "runtime", "wasm"*/])
    fun testToString(type: String) {
        val code = """
            enum class E { A, B, C, D }
            val tested = E.C.toString()
        """.trimIndent()
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
        """.trimIndent()
        MultiTest(code)
            .type { Types.Int }
            .runtime { value -> assertEquals(2, value.castToInt()) }
            .compile("2\n")
            .runTest(type)
    }
}