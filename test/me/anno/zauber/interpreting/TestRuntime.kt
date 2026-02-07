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
            check(value.type == ReturnType.RETURN) {
                "Expected function to return, got $value"
            }
            return runtime to value.value
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
        val expectedType = rt.getClass(ClassType(ArrayType.clazz, listOf(IntType), -1))
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

    // todo test defer and errdefer and destructors
}