package me.anno.zauber.interpreting

import me.anno.MultiTest
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

// todo most relevant topics:
//  lambda-capturing fields, var->Wrapper vs val->ConstructorParam
//  yielding
//  Python parser

class InheritanceClassTests {

    private val stdlib = "\n" + """
package zauber
class Any
class Int
external fun println(arg0: Int)
    """.trimIndent()

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime",/* "js", "java",*/ "c++" /*"wasm"*/])
    fun testFieldInSuperClassIsAssigned(type: String) {
        val code = """
            open class Parent(val y: Int)
            class Child(x: Int): Parent(x)
            val tested = Child(3).y
        """.trimIndent() + stdlib
        MultiTest(code)
            .type { Types.Int }
            .runtime { value ->
                assertEquals(3, value.castToInt())
            }
            .compile("3\n")
            .runTest(type)
    }

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime", "js", "java", "c++", "wasm"])
    fun testCallChildMethod(type: String) {
        val code = """
            open class Parent {
                open fun x() = 0
            }
            class Child: Parent() {
                override fun x() = 1
            }
            fun runTest(p: Parent): Int = p.x()
            val tested = runTest(Child())
        """.trimIndent() + stdlib
        MultiTest(code)
            .type { Types.Int }
            .runtime { value ->
                assertEquals(1, value.castToInt())
            }
            .compile("1\n")
            .runTest(type)
    }

    @Test
    fun testCallChildField() {
        val code = """
            open class Parent {
                open val x get() = 0
            }
            class Child: Parent() {
                override val x get() = 1
            }
            fun runTest(p: Parent): Int = p.x
            val tested = runTest(Child())
            
            package zauber
            class Any
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(1, value.castToInt())
    }

    @Test
    fun testCallParentMethod() {
        val code = """
            open class Parent {
                open fun x() = 0
            }
            class Child: Parent() {
                override fun x() = 1
            }
            fun runTest(p: Parent): Int = p.x()
            val tested = runTest(Parent())
            
            package zauber
            class Any
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(0, value.castToInt())
    }

    @Test
    fun testCallParentField() {
        val code = """
            open class Parent {
                open val x get() = 0
            }
            class Child: Parent() {
                override val x get() = 1
            }
            fun runTest(p: Parent): Int = p.x
            val tested = runTest(Parent())
            
            package zauber
            class Any
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(0, value.castToInt())
    }
}