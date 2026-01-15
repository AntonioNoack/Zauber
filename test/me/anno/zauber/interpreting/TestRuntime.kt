package me.anno.zauber.interpreting

import me.anno.cpp.ast.rich.CppParsingTest.Companion.ensureUnitIsKnown
import me.anno.zauber.resolution.ResolutionUtils.typeResolveScope
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.StringType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TestRuntime {
    companion object {
        fun testExecute(code: String): Pair<Runtime, Instance> {
            val scope = typeResolveScope(code)
            val field = scope.fields.first { it.name == "tested" }
            val getter = field.getter
                ?: throw IllegalStateException("Missing getter for $field")

            val runtime = Runtime()
            Stdlib.registerIntMethods(runtime)
            val value = runtime.executeCall(runtime.getNull(), getter, emptyList())
            return runtime to value
        }
    }

    @Test
    fun testStringField() {
        ensureUnitIsKnown()
        val code = """
            val tested = "Some String"
        """.trimIndent()
        val (_, value) = testExecute(code)
        assertEquals(StringType, value.type.type)
        // todo check content somehow...
    }

    @Test
    fun testSimpleIntCalculation() {
        ensureUnitIsKnown()
        val code = """
            val tested get() = 1+3*7
            
            package zauber
            class Int {
                external fun plus(other: Int): Int
                external fun times(other: Int): Int
            }
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(IntType, value.type.type)
        assertEquals(22, runtime.castToInt(value))
    }

    @Test
    fun testSimpleIntCalculationByCall() {
        val code = """
            val tested get() = sq(5)
            fun sq(x: Int) = x*x
            
            package zauber
            class Int {
                external fun times(other: Int): Int
            }
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(25, runtime.castToInt(value))
    }

    @Test
    fun testSimpleIntField() {
        val code = """
            val tested get() = 17
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(17, runtime.castToInt(value))
    }

    @Test
    fun testHexIntField() {
        val code = """
            val tested get() = 0x17
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(23, runtime.castToInt(value))
    }

    @Test
    fun testBinIntField() {
        val code = """
            val tested get() = 0b10101
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(21, runtime.castToInt(value))
    }

    // todo test defer and errdefer and destructors
}