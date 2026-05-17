package me.anno.zauber.interpreting

import me.anno.MultiTest
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ArithmeticTests {

    private val stdlib = "\n" + """
        package zauber
        external class Int(val content: Int) {
            external fun plus(other: Int): Int
            external fun times(other: Int): Int
            fun inc(): Int = content + 1
       }
       class Any
       object Unit
       external fun println(arg0: Int)
    """.trimIndent()

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime", "js", "java", "c++", "wasm"])
    fun testSimpleIntCalculation(type: String) {
        MultiTest("val tested = 1+3*7$stdlib")
            .type { Types.Int }
            .runtime { value ->
                assertEquals(22, value.castToInt())
            }
            .compile("22\n")
            .runTest(type)
    }

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime", "js", "java", "c++", "wasm"])
    fun testSimpleIntCalculationWithIntermediate(type: String) {
        MultiTest(
            """
            val tested: Int
                get() {
                    var tmp = 1 + 6
                    return tmp * 3
                }
        """.trimIndent() + stdlib
        )
            .type { Types.Int }
            .runtime { value ->
                assertEquals(21, value.castToInt())
            }
            .compile("21\n")
            .runTest(type)
    }

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime"])
    fun testSimpleIntCalculationWithIntermediateAndInc(type: String) {
        // todo this cannot be properly compiled yet :/
        MultiTest(
            """
            val tested: Int
                get() {
                    var tmp = 1 + 6
                    tmp++
                    return tmp * 3
                }
        """.trimIndent() + stdlib
        )
            .type { Types.Int }
            .runtime { value ->
                assertEquals(24, value.castToInt())
            }
            .compile("24\n")
            .runTest(type)
    }

    @Test
    fun testSimpleIntCalculationWithIntermediateAndPlusAssign() {
        val code = """
            val tested: Int
                get() {
                    var tmp = 1+6
                    tmp += 1
                    return tmp * 3
                }
            
            package zauber
            external class Int {
                external fun plus(other: Int): Int
                external fun times(other: Int): Int
                fun inc() = this+1
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(24, value.castToInt())
    }

    @Test
    fun testSimpleIntCalculationByCall() {
        val code = """
            val tested = sq(5)
            fun sq(x: Int) = x*x
            
            package zauber
            external class Int {
                external fun times(other: Int): Int
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(25, value.castToInt())
    }

}