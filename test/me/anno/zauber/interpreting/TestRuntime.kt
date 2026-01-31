package me.anno.zauber.interpreting

import me.anno.support.cpp.ast.rich.CppParsingTest.Companion.ensureUnitIsKnown
import me.anno.zauber.interpreting.RuntimeCast.castToBool
import me.anno.zauber.interpreting.RuntimeCast.castToInt
import me.anno.zauber.logging.LogManager
import me.anno.zauber.resolution.ResolutionUtils.typeResolveScope
import me.anno.zauber.types.Types.ArrayType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.impl.ClassType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf

class TestRuntime {
    companion object {
        fun testExecute(code: String): Pair<Runtime, Instance> {
            val (runtime, value) = testExecuteCatch(code)
            check(value.type == ReturnType.RETURN)
            return runtime to value.instance
        }

        fun testExecuteCatch(code: String): Pair<Runtime, BlockReturn> {
            val scope = typeResolveScope(code)
            val field = scope.fields.first { it.name == "tested" }
            val getter = field.getter
                ?: throw IllegalStateException("Missing getter for $field")

            val runtime = Runtime()
            Stdlib.registerIntMethods(runtime)
            Stdlib.registerFloatMethods(runtime)
            Stdlib.registerStringMethods(runtime)
            Stdlib.registerPrintln(runtime)
            Stdlib.registerArrayAccess(runtime)
            val value = runtime.executeCall(runtime.getNull(), getter, emptyList())
            return runtime to value
        }
    }

    @BeforeEach
    fun init() {
        LogManager.disableLoggers(
            "MethodResolver,Inheritance,MemberResolver," +
                    "TypeResolution,ResolvedField,FieldExpression," +
                    "FieldResolver,ConstructorResolver,ResolvedMethod," +
                    "CallExpression,Field"
        )
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
            class Array<V>(val size: Int): List<V> {
                external operator fun set(index: Int, value: V)
            }
            fun <V> listOf(vararg vs: V): List<V> = vs
            fun <V> arrayOf(vararg vs: V): Array<V> = vs
        """.trimIndent()
        val (runtimeT, valueT) = testExecute("val tested get() = true\n$stdlib")
        assertEquals(true, runtimeT.castToBool(valueT))
        val (runtimeF, valueF) = testExecute("val tested get() = false\n$stdlib")
        assertEquals(false, runtimeF.castToBool(valueF))
    }

    @Test
    fun testArrayOf() {
        ensureUnitIsKnown()
        val (rt, valueT) = testExecute(
            """
            val tested get() = arrayOf(1, 2, 3)
            
            package zauber
            class Array<V>(override val size: Int) {
                external fun set(index: Int, value: V)
            }
            fun <V> arrayOf(vararg vs: V): Array<V> = vs
        """.trimIndent()
        )
        val expectedType = rt.getClass(ClassType(ArrayType.clazz, listOf(IntType)))
        if (false) {
            // todo generics should match...
            //  somehow, we just get an array without any specific generics
            assertEquals(expectedType, valueT.type)
        }
        val contents = valueT.rawValue
        assertInstanceOf<Array<*>>(contents)
        assertEquals(3, contents.size)
        assertEquals(1, rt.castToInt(contents[0] as Instance))
        assertEquals(2, rt.castToInt(contents[1] as Instance))
        assertEquals(3, rt.castToInt(contents[2] as Instance))
    }

    @Test
    fun testFactorialAsRecursiveFunction() {
        // todo this has some sort of cast-problem, too
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

    // todo "const" could be a "deep" value, aka fully immutable
    //  if definitely should be available as some sort of qualifier to protect parameters from mutation

    @Test
    fun testListOf() {
        ensureUnitIsKnown()
        val (rt, value) = testExecute(
            """
            val tested get() = listOf(1, 2, 3)
            
            package zauber
            interface List<V> {
                val size: Int
            }
            class Array<V>(override val size: Int): List<V> {
                external operator fun set(index: Int, value: V)
            }
            fun <V> listOf(vararg vs: V): List<V> = vs
            fun <V> arrayOf(vararg vs: V): Array<V> = vs
        """.trimIndent()
        )
        // todo check contents of array...
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
            class Exception: Throwable()
            class RuntimeException : Exception()
            class NullPointerException : RuntimeException()
            class IllegalArgumentException : RuntimeException()
            
            val tested get() = try {
                throw IllegalArgumentException()
            } catch(e: NullPointerException) {
                2
            }
        """.trimIndent()
        val (_, value) = testExecuteCatch(code)
        check(value.type == ReturnType.THROW)
        val type = value.instance.type.type as ClassType
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
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(2, runtime.castToInt(value))
    }

    // todo this is a late-game test :3
    @Test
    fun testSequenceUsingYield() {
        // todo why can yielded not be resolved???
        //  it should be found in collect-names pass
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
            sealed interface Yieldable<R, T: Throwable, Y> {}
            value class Yielded<R, T: Throwable, Y>(
                val yieldedValue: Y,
                val continueRunning: () -> Yielded<R, T, Y>
            ) : Yieldable<R, T, Y> {}
            value class Thrown<R, T: Throwable, Y>(val value: T) : Yieldable<R, T, Y> {}
            value class Returned<R, T: Throwable, Y>(val value: R) : Yieldable<R, T, Y> {}
            class ArrayList<V>
            interface Function0<R> {
                fun call(): R
            }
            class Any {
                open fun hashCode() = 0
                open fun toString() = ""
                open fun equals(other: Any?): Boolean = other === this
            }
        """.trimIndent()
        val (runtime, value) = testExecute(code)
    }

    // todo test defer and errdefer and destructors
}