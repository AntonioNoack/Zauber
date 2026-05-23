package me.anno

import me.anno.generation.WASMGenerationTests
import me.anno.utils.assertEquals
import me.anno.zauber.logging.LogManager
import org.junit.jupiter.api.Test

class WeirdWhenTest {

    // todo bug: how does this return 0???

    @Test
    fun testWeirdWhenCase() {
        LogManager.enable("ASTSimplifier")
        val code = """
        fun main() {
            var score = 0
            val value = when {
                score >= 12 -> 2
                else -> 1
            }
            println(value)
        }
        package zauber
        class Any
        external class Int(val content: Int) {
            external operator fun plus(other: Int): Int
            external operator fun compareTo(other: Int): Int
        }
        enum class Boolean { TRUE, FALSE }
        class Array<V>(val size: Int) {
            external operator fun get(index: Int): V
            external operator fun set(index: Int, value: V)
        }
        external fun println(arg0: Int)
        """.trimIndent()

        val printed = WASMGenerationTests().generator()
            .testCompileMainAndRun(code) {}
        assertEquals("1\n", printed)
    }
}