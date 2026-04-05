package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.interpreting.RuntimeCast.castToBool
import me.anno.zauber.interpreting.RuntimeCast.castToInt
import me.anno.zauber.logging.LogManager
import me.anno.zauber.resolution.ResolutionUtils.typeResolveScope
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization.Companion.noSpecialization
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BasicRuntimeTests {

    companion object {

        fun createTestRuntime() {
            Stdlib.registerIntMethods()
            Stdlib.registerFloatMethods()
            Stdlib.registerStringMethods()
            Stdlib.registerPrintln()
            Stdlib.registerArrayAccess()
        }

        fun testExecute(code: String): Instance {
            val value = testExecuteCatch(code)
            check(value.type == ReturnType.RETURN) {
                "Expected function to return, got $value"
            }
            return value.value
        }

        fun testExecuteCatch(code: String): BlockReturn {
            val scope = typeResolveScope(code)
            val field = scope.fields.firstOrNull { it.name == "tested" }
                ?: throw IllegalStateException("Missing 'tested' field in scope ${scope.pathStr}")
            val getter = field.getter
                ?: throw IllegalStateException("Missing getter for $field")

            createTestRuntime()
            val getter1 = MethodSpecialization(getter, noSpecialization)
            val self = runtime.getObjectInstance(scope.typeWithArgs)
            val value = runtime.executeCall(self, getter1, emptyList())
            return value
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
        val value = testExecute(code)
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
            
            class Array<V>(val size: Int) {
                external operator fun set(index: Int, value: V)
            }
            fun <V> arrayOf(vararg vs: V): Array<V> = vs
        """.trimIndent()
        val valueT = testExecute("val tested = true\n$stdlib")
        assertEquals(true, runtime.castToBool(valueT))
        val valueF = testExecute("val tested = false\n$stdlib")
        assertEquals(false, runtime.castToBool(valueF))
    }

    @Test
    fun testCreateClassInstance() {
        val code = """
            class Test(val a: Int)
            val tested = Test(5)
        """.trimIndent()
        val value = testExecute(code)
        assertEquals("Test", (value.type.type as ClassType).clazz.name)
        check(value.properties.isNotEmpty()) {
            "${value.type} somehow has no properties"
        }
        val a = value.properties[0]!!
        assertEquals(5, runtime.castToInt(a))
    }

    // todo implement and test destructors...
    // implement and test GC for runtime -> we don't need that, we have Java's GC
}