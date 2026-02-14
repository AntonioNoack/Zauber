package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.RuntimeCast.castToBool
import me.anno.zauber.interpreting.RuntimeCast.castToInt
import me.anno.zauber.logging.LogManager
import me.anno.zauber.resolution.ResolutionUtils.typeResolveScope
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.types.Types.ArrayType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization.Companion.noSpecialization
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
            val getter1 = MethodSpecialization(getter, noSpecialization)
            val self = runtime.getObjectInstance(scope.typeWithArgs)
            val value = runtime.executeCall(self, getter1, emptyList())
            return runtime to value
        }
    }

    @BeforeEach
    fun init() {
        if (false) LogManager.disableLoggers(
            "MethodResolver,Inheritance,MemberResolver," +
                    "TypeResolution,ResolvedField,FieldExpression," +
                    "FieldResolver,ConstructorResolver,ResolvedMethod," +
                    "CallExpression,Field"
        )
    }

    @Test
    fun testPrintln() {
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
        assertEquals(expectedType, valueT.type)
        val contents = valueT.rawValue
        assertInstanceOf<IntArray>(contents)
        assertEquals(listOf(1, 2, 3), contents.toList())
    }

    // todo "const" could be a "deep" value, aka fully immutable
    //  if definitely should be available as some sort of qualifier to protect parameters from mutation

    @Test
    fun testListOf() {
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
        when (val contents = value.rawValue) {
            is IntArray -> {
                // todo it should be this one..., but it's the other one
                val intParam = ParameterList(ArrayType.clazz.typeParameters, listOf(IntType))
                val arrayOfInts = ClassType(ArrayType.clazz, intParam)
                assertEquals(arrayOfInts, value.type.type)
                assertEquals(listOf(1, 2, 3), contents.toList())
            }
            is Array<*> -> {
                assertEquals(ArrayType, value.type.type)
                assertEquals(listOf(1, 2, 3), contents.map { value -> rt.castToInt(value as Instance) })
            }
            else -> throw IllegalStateException("$value is incorrect")
        }
    }

    @Test
    fun testCreateClassInstance() {
        val code = """
            class Test(val a: Int)
            val tested get() = Test(5)
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals("Test", (value.type.type as ClassType).clazz.name)
        val a = value.properties[0]!!
        assertEquals(5, runtime.castToInt(a))
    }

    // todo implement and test destructors...
    // implement and test GC for runtime -> we don't need that, we have Java's GC
}