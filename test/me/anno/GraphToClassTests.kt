package me.anno

import me.anno.generation.WASMGenerationTests
import me.anno.utils.assertEquals
import me.anno.zauber.logging.LogManager
import org.junit.jupiter.api.Test

class GraphToClassTests {
    @Test
    fun testGraphToClass() {
        LogManager.enable("ASTSimplifier")
        val code = """
        fun classifyNumber(n: Int): Int {
            if (n < 0) return -1
            if (n == 0) return 0
        
            var score = 0
        
            /*if (n % 2 == 0) {
                score += 2
            } else {
                score += 1
            }
        
            if (n % 3 == 0) {
                score += 3
            }
        
            if (n % 5 == 0) {
                score += 5
            }*/
        
            var digitSum = 0
            /*var temp = n
        
            while (temp > 0) {
                digitSum += temp % 10
                temp /= 10
            }*/
        
            when {
                digitSum > 30 -> score += 4
                digitSum > 15 -> score += 2
                else -> score += 1
            }
        
            return when {
                score >= 12 -> 4
                score >= 8 -> 3
                score >= 4 -> 2
                else -> 1
            }
        }
        fun main() {
            println(classifyNumber(7))
        }
        package zauber
        class Any
        external class Int(val content: Int) {
            external operator fun plus(other: Int): Int
            external operator fun minus(other: Int): Int
            external operator fun times(other: Int): Int
            external operator fun div(other: Int): Int
            external operator fun rem(other: Int): Int
            external operator fun compareTo(other: Int): Int
            external operator fun equals(other: Int): Boolean
            operator fun inc(): Int = this + 1
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
        assertEquals("21\n", printed)
    }
}