package me.anno.zauber.interpreting

import me.anno.MultiTest
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class OverloadTests {

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime", "js", "java", "c++", "wasm"])
    fun testMethodOverloads(type: String) {
        MultiTest("""
            fun f(x: Int) = 12
            fun f(x: Float) = 4
            val tested = f(1) / f(1f)
            
            package zauber
            class Any
            class Int {
                external fun div(other: Int): Int
            }
            external fun println(arg0: Int)
        """.trimIndent())
            .type { Types.Int }
            .runtime { value -> assertEquals(3, value.castToInt()) }
            .compile("3\n")
            .runTest(type)
    }
}