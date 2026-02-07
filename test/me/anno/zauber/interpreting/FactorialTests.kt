package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.RuntimeCast.castToInt
import me.anno.zauber.interpreting.TestRuntime.Companion.testExecute
import me.anno.zauber.logging.LogManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FactorialTests {

    @Test
    fun testFactorialAsRecursiveFunction() {
        val code = """
            fun fac(i: Int): Int {
                if (i <= 1) return 1
                return i * fac(i-1)
            }
            val tested get() = fac(5)
            
            package zauber
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
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(5 * 4 * 3 * 2, runtime.castToInt(value))
    }

    @Test
    fun testFactorialAsWhileLoop() {
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
            val tested get() = fac(10)
            
            package zauber
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
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(10 * 9 * 8 * 7 * 6 * 5 * 4 * 3 * 2, runtime.castToInt(value))
    }

    @Test
    fun testFactorialAsForLoop() {
        LogManager.disableLoggers(
            "Inheritance,MemberResolver,MemberResolver,FieldExpression,FieldResolver,TypeResolution," +
                    "CallExpression,ConstructorResolver,ResolvedMethod,MethodResolver,ResolvedField,Field"
        )
        val code = """
            fun fac(n: Int): Int {
                var product = 1
                for (k in 2 .. n) {
                    product *= k
                }
                return product
            }
            val tested get() = fac(10)
            
            package zauber
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
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(10 * 9 * 8 * 7 * 6 * 5 * 4 * 3 * 2, runtime.castToInt(value))
    }

}