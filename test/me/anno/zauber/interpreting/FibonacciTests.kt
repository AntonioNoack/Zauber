package me.anno.zauber.interpreting

import me.anno.MultiTest
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.logging.LogManager
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * test some different language features to compute an actual task: Fibonacci numbers
 * 1, 1, 2, 3, 5, 8, 13, 21
 * */
class FibonacciTests {

    private val stdlib = "\n" + """
        package zauber
        object Unit
        class Int {
            external operator fun plus(other: Int): Int
            external operator fun minus(other: Int): Int
            external operator fun compareTo(other: Int): Int
            operator fun until(other: Int): IntRange = IntRange(this, other)
            operator fun rangeTo(other: Int): IntRange = IntRange(this, other+1)
            operator fun equals(other: Int): Boolean = this >= other && this <= other
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
            external operator fun get(i: Int): V
            external operator fun set(i: Int, value: V)
        }
        
        class Any {
            open fun equals(other: Any?): Boolean = this === other
        }
        
        external fun println(arg0: Int)
        """.trimIndent()

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime", "js", "java", "c++", "wasm"])
    fun testForLoopFibonacci(type: String) {
        val code = """
        fun fib(i: Int): Int {
            var a = 1
            var b = 0
            for (k in 0 .. i) {
                val tmp = a
                a = a + b
                b = tmp
            }
            return b
        }
        val tested = fib(7)
        """.trimIndent() + stdlib
        MultiTest(code)
            .type { Types.Int }
            .runtime { value -> assertEquals(21, value.castToInt()) }
            .compile("21\n")
            .runTest(type)
        val value = testExecute(code)
        assertEquals(21, value.castToInt())
    }

    @Test
    fun testWhileLoopFibonacci() {
        val code = """
        fun fib(i: Int): Int {
            var a = 1
            var b = 0
            var k = 0
            while (k <= i) {
                val tmp = a
                a = a + b
                b = tmp
                k++
            }
            return b
        }
        val tested = fib(7)
        """.trimIndent() + stdlib
        // 1, 1, 2, 3, 5, 8, 13, 21
        val value = testExecute(code)
        assertEquals(21, value.castToInt())
    }

    @Test
    fun testRecursiveFibonacci() {
        val code = """
        fun fib(i: Int): Int {
            if (i < 2) return 1
            return fib(i-1) + fib(i-2)
        }
        val tested = fib(5)
        $stdlib
        """.trimIndent()
        // 1, 1, 2, 3, 5, 8
        val value = testExecute(code)
        assertEquals(8, value.castToInt())
    }

    @Test
    fun testTailRecursiveFibonacci() {
        val code = """
        tailrec fun fib(i: Int, a: Int, b: Int): Int {
            if (i < 2) return b
            return fib(i - 1, b, a + b)
        }
        val tested = fib(5,1,1)
        """.trimIndent() + stdlib

        val value = testExecute(code)
        assertEquals(8, value.castToInt())
    }

    @Test
    fun testTailRecursiveDefaultParamsFibonacci() {
        val code = """
        tailrec fun fib(i: Int, a: Int = 1, b: Int = 1): Int {
            if (i < 2) return b
            return fib(i - 1, b, a + b)
        }
        val tested = fib(5)
        """.trimIndent() + stdlib

        val value = testExecute(code)
        assertEquals(8, value.castToInt())
    }

    @Test
    fun testWhenExpressionFibonacci() {
        val code = """
        fun fib(i: Int): Int = when (i) {
            0, 1 -> 1
            else -> fib(i - 1) + fib(i - 2)
        }
        val tested = fib(5)
        """.trimIndent() + stdlib

        val value = testExecute(code)
        assertEquals(8, value.castToInt())
    }

    @Test
    fun testLocalFunctionFibonacci() {
        val code = """
        fun fib(i: Int): Int {
            fun go(n: Int): Int {
                if (n < 2) return 1
                return go(n - 1) + go(n - 2)
            }
            return go(i)
        }
        val tested = fib(5)
        """.trimIndent() + stdlib

        val value = testExecute(code)
        assertEquals(8, value.castToInt())
    }

    @Test
    fun testMemoizedFibonacci() {
        LogManager.disableLoggers(
            "TypeResolution,CallExpression," +
                    "ConstructorResolver,MemberResolver," +
                    "FieldExpression,FieldResolver,ResolvedField,Field," +
                    "MethodResolver,ResolvedMethod," +
                    "Inheritance,ASTSimplifier,Runtime," +
                    "SimpleGetField,SimpleSetField"
        )

        // todo try to implement property capture on this sample...

        val code = """
        fun fib(i: Int): Int {
            val memory = IntArray(i + 1)
            fun go(n: Int): Int {
                if (n < 2) return 1
                if (memory[n] != 0) return memory[n]
                memory[n] = go(n - 1) + go(n - 2)
                return memory[n]
            }
            return go(i)
        }
        val tested = fib(5)
        package zauber
        typealias IntArray = Array<Int>
        """.trimIndent() + stdlib

        val value = testExecute(code)
        assertEquals(8, value.castToInt())
    }

    @Test
    fun testPropertyGetterFibonacci() {
        val code = """
        class Fib(val i: Int) {
            val value: Int
                get() {
                    if (i < 2) return 1
                    return Fib(i - 1).value + Fib(i - 2).value
                }
        }
        val tested = Fib(5).value
        """.trimIndent() + stdlib

        val value = testExecute(code)
        assertEquals(8, value.castToInt())
    }


}