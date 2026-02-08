package me.anno.zauber.interpreting

import me.anno.support.cpp.ast.rich.CppParsingTest.Companion.ensureUnitIsKnown
import me.anno.zauber.interpreting.RuntimeCast.castToInt
import me.anno.zauber.interpreting.RuntimeCast.castToString
import me.anno.zauber.interpreting.TestRuntime.Companion.testExecute
import me.anno.zauber.interpreting.TestRuntime.Companion.testExecuteCatch
import me.anno.zauber.logging.LogManager
import me.anno.zauber.types.impl.ClassType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TryCatchTests {

    @Test
    fun testTryCatchNormal() {
        ensureUnitIsKnown()
        val code = """
            class Exception: Throwable()
            class RuntimeException : Exception()
            class NullPointerException : RuntimeException()
            
            val tested get() = try {
                1
            } catch(e: NullPointerException) {
                2
            }
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(1, runtime.castToInt(value))
    }

    @Test
    fun testTryCatchMismatch() {
        ensureUnitIsKnown()
        val code = """
            val tested get() = try {
                throw IllegalArgumentException()
            } catch(e: NullPointerException) {
                2
            }
            
            package zauber
            class Exception: Throwable()
            class RuntimeException : Exception()
            class NullPointerException : RuntimeException()
            class IllegalArgumentException : RuntimeException()
            enum class Boolean { TRUE, FALSE }
            class Array<V>(val size: Int) {
                external fun set(index: Int, value: V)
            }
        """.trimIndent()
        val (_, value) = testExecuteCatch(code)
        check(value.type == ReturnType.THROW)
        val type = value.value.type.type as ClassType
        check(type.clazz.name == "IllegalArgumentException")
    }

    @Test
    fun testTryCatchCatching() {
        ensureUnitIsKnown()
        val code = """
            val tested get() = try {
                throw NullPointerException()
            } catch(e: NullPointerException) {
                2   
            }
            
            package zauber
            class Exception: Throwable()
            class RuntimeException : Exception()
            class NullPointerException : RuntimeException()
            enum class Boolean { TRUE, FALSE }
            class Array<V>(val size: Int) {
                external fun set(index: Int, value: V)
            }
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(2, runtime.castToInt(value))
    }

    @Test
    fun testSimpleFinallyIsExecuted() {
        ensureUnitIsKnown()
        val code = """
            val tested get() = try {
                "Test"
            } finally {
                println("Hello World")
            }
            
            package zauber
            class String
            external fun println(str: String)
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals("Test", runtime.castToString(value))
        assertEquals(listOf("Hello World"), runtime.printed)
    }

    @Test
    fun testCascadedFinallyIsExecuted() {
        LogManager.disableLoggers(
            "MemberResolver,Inheritance,TypeResolution,CallExpression,ConstructorResolver," +
                    "MethodResolver,ResolvedMethod"
        )
        ensureUnitIsKnown()
        val code = """
            val tested get() = try {
                try {
                    "Test"
                } finally {
                    println("Hello ")
                }
            } finally {
                println("World")
            }
            
            package zauber
            class String
            external fun println(str: String)
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals("Test", runtime.castToString(value))
        assertEquals(listOf("Hello ", "World"), runtime.printed)
    }

    @Test
    fun testLoopedFinallyIsExecuted() {
        LogManager.disableLoggers(
            "TypeResolution,MemberResolver,Inheritance,ResolvedMethod," +
                    "MethodResolver,CallExpression,Field,ConstructorResolver,ResolvedField,FieldResolver," +
                    "FieldExpression"
        )
        ensureUnitIsKnown()
        val code = """
            val tested: String get() {
                for (i in 0 until 4) {
                    try {} finally {
                        println(i)
                    }
                }
                return "Test"
            }
            
            package zauber
            external fun println(value: Int)
            
            // stdlib for for-loop:
            class Int {
                external operator fun plus(other: Int): Int
                external operator fun minus(other: Int): Int
                external operator fun compareTo(other: Int): Int
                operator fun until(other: Int): IntRange = IntRange(this, other)
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
        assertEquals("Test", runtime.castToString(value))
        assertEquals(listOf("0", "1", "2", "3"), runtime.printed)
    }

}