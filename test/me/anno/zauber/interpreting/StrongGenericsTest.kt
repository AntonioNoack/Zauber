package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.interpreting.RuntimeCast.castToBool
import me.anno.zauber.interpreting.RuntimeCast.castToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// todo do we want to use need-to-use ::class???
//  benefits:
//  - class is clearly visible as such
//  - object vs class is clear
//  negatives:
//  - often extra writing for what...

class StrongGenericsTest {
    @Test
    fun testGenericsHaveNameNativeType() {
        val code = """
            fun <V> call(v: V) = V.name
            val tested = call(0)
        """.trimIndent()
        val value = testExecute(code)
        assertEquals("Int", castToString(value))
    }

    @Test
    fun testGenericsHaveNameReferenceType() {
        val code = """
            fun <V> call(v: V) = V.name
            val tested = call("Hello")
        """.trimIndent()
        val value = testExecute(code)
        assertEquals("String", castToString(value))
    }

    @Test
    fun testGenericsAsCondition() {
        val trueCode = """
            fun <V> call(v: V): Boolean = V.isSubTypeOf(Int)
            val tested = call(0)
        """.trimIndent()
        val valueT = testExecute(trueCode)
        assertEquals(true, castToBool(valueT))
        val falseCode = """
            fun <V> call(v: V): Boolean = V.isSubTypeOf(Int)
            val tested = call(0f)
        """.trimIndent()
        val valueF = testExecute(falseCode)
        assertEquals(false, castToBool(valueF))
    }
}