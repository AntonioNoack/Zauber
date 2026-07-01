package me.anno.zauber.interpreting

import me.anno.utils.MultiTest
import me.anno.zauber.ast.simple.fields.LocalField.Companion.maxNumLocalFields
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.logging.LogManager
import me.anno.zauber.types.Types
import me.anno.utils.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class JumpToLabelTests {

    @Test
    fun testBreakToNamed() {
        val code = """
            fun call(): Int {
                named@while(true) {
                    while(true) {
                        // if this was an unnamed break, we would fail
                        break@named
                        error("Skipped break") // just in case
                    }
                }
                return 1
            }
            
            val tested = call()
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(1, value.castToInt())
    }

    @Test
    fun testContinueToNamedWhile() {
        val code = """
            fun call(): Int {
                var x = 1
                named@while(x<100) {
                    x *= 2
                    if (x < 10) continue@named
                    else break@named
                }
                return x
            }
            
            val tested = call()
        """.trimIndent()
        val value = testExecute(code, reset = false)
        assertEquals(16, value.castToInt())
    }

    @Test
    fun testContinueToNamedDoWhile() {
        LogManager.enable("ASTSimplifier")

        val code = """
            fun call(): Int {
                var x = 1
                named@ do {
                    x *= 2
                    if (x < 10) continue@named
                    else break@named
                } while(x < 100)
                return x
            }
            
            val tested = call()
        """.trimIndent()
        val value = testExecute(code, reset = false)
        assertEquals(16, value.castToInt())
    }

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime", "js", /*"java", "c++", */"wasm"])
    fun testContinueToFor(type: String) {

        maxNumLocalFields = 20

        val code = """
            fun call(): Int {
                var x = 1
                named@for(k in 0 until 10) {
                    x *= 2
                    if (x < 10) continue@named
                    else break@named
                }
                return x
            }
            
            val tested = call()
        """.trimIndent()

        MultiTest(code)
            .type { Types.Int }
            .runtime { value ->
                assertEquals(16, value.castToInt())
            }
            .compile("16\n")
            .runTest(type)
    }

}