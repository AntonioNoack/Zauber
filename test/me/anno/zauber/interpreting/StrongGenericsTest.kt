package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.RuntimeCast.castToBool
import me.anno.zauber.interpreting.RuntimeCast.castToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StrongGenericsTest {
    @Test
    fun testGenericsHaveNameNativeType() {
        val code = """
            fun <V> call(v: V) = V.name
            val tested = call(0)
        """.trimIndent()
        val (rt, value) = testExecute(code)
        assertEquals("Int", rt.castToString(value))
    }

    @Test
    fun testGenericsHaveNameReferenceType() {
        val code = """
            fun <V> call(v: V) = V.name
            val tested = call("Hello")
        """.trimIndent()
        val (rt, value) = testExecute(code)
        assertEquals("String", rt.castToString(value))
    }

    @Test
    fun testGenericsAsCondition() {
        val trueCode = """
            fun <V> call(v: V): Boolean = V.isSubTypeOf(Int)
            val tested = call(0)
        """.trimIndent()
        val (rtT, valueT) = testExecute(trueCode)
        assertEquals(true, rtT.castToBool(valueT))
        val falseCode = """
            fun <V> call(v: V): Boolean = V.isSubTypeOf(Int)
            val tested = call(0f)
        """.trimIndent()
        val (rtF, valueF) = testExecute(falseCode)
        assertEquals(false, rtF.castToBool(valueF))
    }
}