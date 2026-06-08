package me.anno.zauber.interpreting

import me.anno.utils.ResolutionUtils.typeResolveScope
import me.anno.utils.assertEquals
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.interpreting.Stdlib.registerAllMethods
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.impl.ClassType
import org.junit.jupiter.api.Test

class BasicRuntimeTests {

    companion object {

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
                ?: error("Missing 'tested' field in scope ${scope.pathStr}")
            val getter = field.getter
                ?: error("Missing getter for $field")

            registerAllMethods()

            val specialization = Specialization.fromSimple(getter.scope)
            val self = runtime.getObjectInstance(scope)
            return runtime.executeCall(self, null, specialization, emptyList())
        }
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
        assertEquals("Hello World!\n", runtime.printed.toString())
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