package me.anno.zauber.interpreting

import me.anno.MultiTest
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.logging.LogManager
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class JumpToLabelTests {

    private val stdlib = "\n" + """
        package zauber
        class Any
        external class Int {
            external operator fun plus(other: Int): Int
            external operator fun times(other: Int): Int
            external operator fun compareTo(other: Int): Int
            operator fun inc(): Int = this + 1
            
            operator fun until(other: Int): IntRange = IntRange(this, other)
        }
        
        // small, mutable IntRange-iterator
        class IntRange(var start: Int, val end: Int) {
            fun iterator() = this
            fun hasNext() = start < end
            fun next(): Int {
                val value = start
                start = value + 1
                return value
            }
        }
        
        class Throwable(val message: String)
        class IllegalStateException(message: String): Throwable(message)
            
        enum class Boolean { TRUE, FALSE }
        class Array<V>(val size: Int) {
            external operator fun set(index: Int, value: V)
        }
        
        object Unit
        external fun println(arg0: Int)
    """.trimIndent()

    @Test
    fun testBreakToNamed() {
        val code = """
            fun call(): Int {
                named@while(true) {
                    while(true) {
                        // if this was an unnamed break, we would fail
                        break@named
                        throw IllegalStateException("Skipped break") // just in case
                    }
                }
                return 1
            }
            
            val tested = call()
        """.trimIndent() + stdlib
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
        """.trimIndent() + stdlib
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
        """.trimIndent() + stdlib
        val value = testExecute(code, reset = false)
        assertEquals(16, value.castToInt())
    }

    @ParameterizedTest
    @ValueSource(strings = ["type", /*"runtime", "js", "java", "c++",*/ "wasm"])
    fun testContinueToFor(type: String) {
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
        """.trimIndent() + stdlib

        MultiTest(code)
            .type { Types.Int }
            .runtime { value ->
                assertEquals(16, value.castToInt())
            }
            .compile("16\n")
            .runTest(type)
    }

}