package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// do we want to use need-to-use ::class?
//  benefits:
//  - class is clearly visible as such
//  - object vs class is clear
//  negatives:
//  - often extra writing for what...
class StrongGenericsTest {
    companion object {
        val stdlib = """
            package zauber
            
            enum class Boolean { TRUE, FALSE }
            object Unit
            class Array<V>(val size: Int) {
                external operator fun set(index: Int, value: V)
            }
            
            class Type {
                external fun isSubTypeOf(other: Type): Boolean
            }
            class ClassType<V>(val name: String): Type()
            
            class Throwable(message: String)
            class NullPointerException(message: String): Throwable(message)
            fun throwNPE(message: String) = throw NullPointerException(message)
        """.trimIndent()
    }

    @Test
    fun testGenericsHaveNameNativeType() {
        // todo when we don't define the return-type for call, we somehow get an error :(
        val code = """
            fun <V> call(v: V): String = (V::class as ClassType).name
            val tested = call(0)
        """.trimIndent() + stdlib
        val value = testExecute(code)
        assertEquals("Int", value.castToString())
    }

    @Test
    fun testValuesHaveNameNativeType() {
        val code = """
            fun <V> call(v: V) = v::class.name
            val tested = call(0)
        """.trimIndent() + stdlib
        val value = testExecute(code)
        assertEquals("Int", value.castToString())
    }

    @Test
    fun testGenericsHaveNameReferenceType() {
        val code = """
            fun <V> call(v: V): String = (V::class as ClassType).name
            val tested = call("Hello")
        """.trimIndent() + stdlib
        val value = testExecute(code)
        assertEquals("String", value.castToString())
    }

    @Test
    fun testGenericsAsCondition() {
        val trueCode = """
            fun <V> call(v: V): Boolean = V::class.isSubTypeOf(Int::class)
            val tested = call(0)
        """.trimIndent() + stdlib
        val valueT = testExecute(trueCode)
        assertEquals(true, valueT.castToBool())
        val falseCode = """
            fun <V> call(v: V): Boolean = V::class.isSubTypeOf(Int::class)
            val tested = call(0f)
        """.trimIndent() + stdlib
        val valueF = testExecute(falseCode)
        assertEquals(false, valueF.castToBool())
    }

    @Test
    fun testValuesAsCondition() {
        val trueCode = """
            fun <V> call(v: V): Boolean = v::class.isSubTypeOf(Int::class)
            val tested = call(0)
        """.trimIndent() + stdlib
        val valueT = testExecute(trueCode)
        assertEquals(true, valueT.castToBool())
        val falseCode = """
            fun <V> call(v: V): Boolean = v::class.isSubTypeOf(Int::class)
            val tested = call(0f)
        """.trimIndent() + stdlib
        val valueF = testExecute(falseCode)
        assertEquals(false, valueF.castToBool())
    }
}