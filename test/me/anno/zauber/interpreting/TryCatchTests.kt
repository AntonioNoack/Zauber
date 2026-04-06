package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecuteCatch
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.logging.LogManager
import me.anno.zauber.types.impl.ClassType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TryCatchTests {

    @Test
    fun testTryCatchNormal() {
        val code = """
            class Exception: Throwable()
            class RuntimeException : Exception()
            class NullPointerException : RuntimeException()
            
            val tested = try {
                1
            } catch(e: NullPointerException) {
                2
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(1, value.castToInt())
    }

    @Test
    fun testTryCatchMismatch() {
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
        val value = testExecuteCatch(code)
        check(value.type == ReturnType.THROW)
        val type = value.value.clazz.type as ClassType
        check(type.clazz.name == "IllegalArgumentException")
    }

    @Test
    fun testTryCatchCatching() {
        val code = """
            val tested = try {
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
        val value = testExecute(code)
        assertEquals(2, value.castToInt())
    }

    @Test
    fun testLateinitUninitialized() {
        val code = """
            lateinit var likeNull: Int
            val tested: Int get() = try {
                likeNull
            } catch(e: NullPointerException) {
                2
            }
            
            package zauber
            class Throwable(val message: String?)
            class Exception(message: String?): Throwable(message)
            class NullPointerException(message: String?) : Exception(message)
            enum class Boolean { TRUE, FALSE }
            fun throwJNE(name: String): Nothing {
                throw NullPointerException(name)
            }
            class Array<V>(val size: Int) {
                external fun set(index: Int, value: V)
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(2, value.castToInt())
    }

    @Test
    fun testNullPointerExceptionManual() {
        val code = """
            val likeNull: Int? = null
            val tested = try {
                likeNull ?: throw NullPointerException("Missing likeNull")
            } catch(e: NullPointerException) {
                2
            }
            
            package zauber
            class Throwable(val message: String?)
            class Exception(message: String?): Throwable(message)
            class NullPointerException(message: String?) : Exception(message)
            enum class Boolean { TRUE, FALSE }
            class Array<V>(val size: Int) {
                external fun set(index: Int, value: V)
            }
            class Any
            class Int {
               fun equals(other: Any?) = other is Int
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(2, value.castToInt())
    }

    @Test
    fun testNullPointerExceptionByNPECall() {
        val code = """
            val likeNull: Int? = null
            val tested = try {
                likeNull!!
            } catch(e: NullPointerException) {
                2
            }
            
            package zauber
            class Throwable(val message: String?)
            class Exception(message: String?): Throwable(message)
            class NullPointerException(message: String?) : Exception(message)
            enum class Boolean { TRUE, FALSE }
            class Array<V>(val size: Int) {
                external fun set(index: Int, value: V)
            }
            fun throwNPE(message: String): Nothing {
                throw NullPointerException(message)
            }
            class Any
            class Int {
               fun equals(other: Any?) = other is Int
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(2, value.castToInt())
    }

    @Test
    fun testSimpleFinallyIsExecuted() {
        val code = """
            val tested = try {
                "Test"
            } finally {
                println("Hello World")
            }
            
            package zauber
            class String
            external fun println(str: String)
        """.trimIndent()
        val value = testExecute(code)
        assertEquals("Test", value.castToString())
        assertEquals(listOf("Hello World"), runtime.printed)
    }

    @Test
    fun testCascadedFinallyIsExecuted() {
        LogManager.disableLoggers(
            "MemberResolver,Inheritance,TypeResolution,CallExpression,ConstructorResolver," +
                    "MethodResolver,ResolvedMethod"
        )
        val code = """
            val tested = try {
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
        val value = testExecute(code)
        assertEquals("Test", value.castToString())
        assertEquals(listOf("Hello ", "World"), runtime.printed)
    }

    @Test
    fun testLoopedFinallyIsExecuted() {
        LogManager.disableLoggers(
            "TypeResolution,MemberResolver,Inheritance,ResolvedMethod," +
                    "MethodResolver,CallExpression,Field,ConstructorResolver,ResolvedField,FieldResolver," +
                    "FieldExpression"
        )
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
        val value = testExecute(code)
        assertEquals("Test", value.castToString())
        assertEquals(listOf("0", "1", "2", "3"), runtime.printed)
    }

}