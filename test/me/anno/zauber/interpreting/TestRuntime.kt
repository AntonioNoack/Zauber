package me.anno.zauber.interpreting

import me.anno.cpp.ast.rich.CppParsingTest.Companion.ensureUnitIsKnown
import me.anno.zauber.resolution.ResolutionUtils.typeResolveScope
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.impl.ClassType
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
            Stdlib.registerPrintln(runtime)
            val value = runtime.executeCall(runtime.getNull(), getter, emptyList())
            check(value.type == ReturnType.RETURN)
            return runtime to value.instance
        }
    }

    @Test
    fun testStringField() {
        ensureUnitIsKnown()
        val code = """
            val tested = "Some String"
        """.trimIndent()
        val (rt, value) = testExecute(code)
        assertEquals(StringType, value.type.type)
        assertEquals("Some String", rt.castToString(value))
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

    @Test
    fun testPrintln() {
        ensureUnitIsKnown()
        val code = """
            val tested: Int get() {
                println("Hello World!")
                return 0
            }
            package zauber
            class String
            external fun println(str: String)
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(listOf("Hello World!"), runtime.printed)
        assertEquals(0, runtime.castToInt(value))
    }

    @Test
    fun testBoolean() {
        ensureUnitIsKnown()
        val stdlib = """
            package zauber
            enum class Boolean {
                TRUE, FALSE
            }
            interface List<V>
            class ArrayWrapper<V>(val vs: Array<V>): List<V>
            fun <V> listOf(vararg vs: V): List<V> = ArrayWrapper(vs)
            fun <V> arrayOf(vararg vs: V): Array<V> = vs // idk, recursive definition, kind of...
        """.trimIndent()
        val (runtimeT, valueT) = testExecute("val tested get() = true\n$stdlib")
        assertEquals(true, runtimeT.castToBool(valueT))
        val (runtimeF, valueF) = testExecute("val tested get() = false\n$stdlib")
        assertEquals(false, runtimeF.castToBool(valueF))
    }

    @Test
    fun testCreateClassInstance() {
        ensureUnitIsKnown()
        val code = """
            class Test(val a: Int)
            val tested get() = Test(5)
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals("Test", (value.type.type as ClassType).clazz.name)
        val a = value.properties[0]!!
        assertEquals(5, runtime.castToInt(a))
    }

    // todo this is a late-game test :3
    @Test
    fun testSequenceUsingYield() {
        ensureUnitIsKnown()
        val code = """
            fun <V> collectYielded(runnable: () -> Unit): List<V> {
                var yielded = async runnable()
                val result = ArrayList<V>()
                while (yielded is Yielded<*, *, V>) {
                    result.add(yielded.yieldedValue)
                    yielded = async yielded.continueRunning()
                }
                if (yielded is Thrown<*, *, *>) {
                    throw yielded.thrown;
                }
                return result
            }
            
            val tested get() = collectYielded<Int> {
                yield 1
                yield 2
                yield 3
            }
            
            package zauber
            // a little clusterfuck
            sealed interface Yieldable<R, T : Throwable, Y> {}
            value class Yielded<R, T : Throwable, Y>(
                val yieldedValue: Y,
                val continueRunning: () -> Yielded<R, T, Y>
            ) : Yieldable<R, T, Y> {}
            value class Thrown<R, T : Throwable, Y>(val value: T) : Yieldable<R, T, Y> {}
            value class Returned<R, T, Y>(val value: R) : Yieldable<R, T, Y> {}
            // todo define ArrayList
        """.trimIndent()
        val (runtime, value) = testExecute(code)
    }

    // todo test defer and errdefer and destructors
}