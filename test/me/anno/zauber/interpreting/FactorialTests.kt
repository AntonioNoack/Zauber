package me.anno.zauber.interpreting

import me.anno.MultiTest
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class FactorialTests {

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime", "js"/*, "java", "c++", "wasm"*/])
    fun testFactorialAsRecursiveFunction(type: String) {
        // todo for this to work, we must implement SimpleCompare in all targets
        val code = """
            fun fac(i: Int): Int {
                if (i <= 1) return 1
                return i * fac(i-1)
            }
            val tested = fac(5)
            
            package zauber
            class Any
            class Int {
                external fun compareTo(other: Int): Int
                external fun times(other: Int): Int
                external fun minus(other: Int): Int
            }
            
            enum class Boolean {
                TRUE, FALSE
            }
            
            object Unit
            
            interface List<V>
            class Array<V>(val size: Int): List<V> {
                external operator fun set(index: Int, value: V)
            }
            fun <V> arrayOf(vararg vs: V): Array<V> = vs
            external fun println(arg0: Int)
        """.trimIndent()
        MultiTest(code)
            .type { Types.Int }
            .runtime { value -> assertEquals(5 * 4 * 3 * 2, value.castToInt()) }
            .compile("120\n")
            .runTest(type)
    }

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime", "js"/*, "java", "c++", "wasm"*/])
    fun testFactorialAsWhileLoop(type: String) {
        val code = """
            fun fac(i: Int): Int {
                var f = 1
                var i = i
                while (i > 1) {
                    f *= i
                    i--
                }
                return f
            }
            val tested = fac(10)
            
            package zauber
            class Any
            class Int {
                external fun compareTo(other: Int): Int
                external fun times(other: Int): Int
                external fun minus(other: Int): Int
                fun dec() = this - 1
            }
            
            enum class Boolean {
                TRUE, FALSE
            }
            
            object Unit
            
            interface List<V>
            class Array<V>(val size: Int): List<V> {
                external operator fun set(index: Int, value: V)
            }
            fun <V> arrayOf(vararg vs: V): Array<V> = vs
            external fun println(arg0: Int)
        """.trimIndent()
        val value1 = 10 * 9 * 8 * 7 * 6 * 5 * 4 * 3 * 2
        MultiTest(code)
            .type { Types.Int }
            .runtime { value -> assertEquals(value1, value.castToInt()) }
            .compile("$value1\n")
            .runTest(type)
    }

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime", "js"/*, "java", "c++", "wasm"*/])
    fun testFactorialAsForLoop(type: String) {
        val stdlib = """
            package zauber
            class Any
            object Unit
            class Int {
                external operator fun plus(other: Int): Int
                external operator fun minus(other: Int): Int
                external operator fun times(other: Int): Int
                external operator fun compareTo(other: Int): Int
                operator fun until(other: Int): IntRange = IntRange(this, other)
                operator fun rangeTo(other: Int): IntRange = IntRange(this, other+1)
                fun inc() = this+1
                fun dec() = this-1
            }
            
            class IntRange(val from: Int, val to: Int) {
                fun iterator() = IntRangeIterator(this)
            }
            
            interface Iterator<V> {
                fun next(): Int
                fun hasNext(): Boolean
            }
            
            class IntRangeIterator(val range: IntRange): Iterator<Int> {
                var index = range.from
                override fun hasNext(): Boolean = index < range.to
                override fun next(): Int = index++
            }
            
            enum class Boolean { TRUE, FALSE }
            class Array<V>(val size: Int) {
                external operator fun get(i: Int)
                external operator fun set(i: Int, value: V)
            }
            external fun println(arg0: Int)
        """.trimIndent()
        val code = """
            fun fac(n: Int): Int {
                var product = 1
                for (k in 2 .. n) {
                    product *= k
                }
                return product
            }
            val tested = fac(10)
        """.trimIndent() + stdlib

        // todo bug: why is the generated source code pretty much ending after the iterator call?

        val value1 = 10 * 9 * 8 * 7 * 6 * 5 * 4 * 3 * 2
        MultiTest(code)
            .type { Types.Int }
            .runtime { value -> assertEquals(value1, value.castToInt()) }
            .compile("$value1\n")
            .runTest(type)
    }

}