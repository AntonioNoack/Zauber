package me.anno.zauber.interpreting

import me.anno.utils.ResolutionUtils.typeResolveScope
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BasicRuntimeTests {

    companion object {

        fun createTestRuntime() {
            Stdlib.registerSmallIntMethods()
            Stdlib.registerIntMethods()
            Stdlib.registerFloatMethods()
            Stdlib.registerStringMethods()
            Stdlib.registerPrintln()
            Stdlib.registerArrayAccess()
            Stdlib.registerTypeMethods()
        }

        fun testExecute(code: String, reset: Boolean = true): Instance {
            val value = testExecuteCatch(code, reset)
            check(value.type == ReturnType.RETURN) {
                "Expected function to return, got $value"
            }
            return value.value
        }

        fun testExecuteCatch(code: String, reset: Boolean = true): BlockReturn {
            val scope = typeResolveScope(code, reset)[ScopeInitType.AFTER_DISCOVERY]
            val field = scope.fields.firstOrNull { it.name == "tested" }
                ?: throw IllegalStateException("Missing 'tested' field in scope ${scope.pathStr}")
            val getter = field.getter
                ?: throw IllegalStateException("Missing getter for $field")

            createTestRuntime()

            val specialization = Specialization(getter.scope, emptyParameterList())
            val getter1 = MethodSpecialization(getter, specialization)
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
        assertEquals(0, value.castToInt())
    }

    @Test
    fun testBoolean() {
        val stdlib = """
            package zauber
            class Any
            enum class Boolean {
                TRUE, FALSE
            }
            
            class Array<V>(val size: Int) {
                external operator fun set(index: Int, value: V)
            }
            fun <V> arrayOf(vararg vs: V): Array<V> = vs
        """.trimIndent()
        val valueT = testExecute("val tested = true\n$stdlib")
        assertEquals(true, valueT.castToBool())
        val valueF = testExecute("val tested = false\n$stdlib")
        assertEquals(false, valueF.castToBool())
    }

    @Test
    fun testCreateClassInstance() {
        val code = """
            class Test(val a: Int)
            val tested = Test(5)
            
            package zauber
            class Any
        """.trimIndent()
        val value = testExecute(code)
        assertEquals("Test", (value.clazz.type as ClassType).clazz.name)
        check(value.fields.isNotEmpty()) {
            "${value.clazz} somehow has no properties"
        }
        val a = value.fields[0]!!
        assertEquals(5, a.castToInt())
    }

    // todo implement and test destructors...
    // implement and test GC for runtime -> we don't need that, we have Java's GC
}