package me.anno.zauber.interpreting

import me.anno.utils.MultiTest
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.types.Types
import me.anno.utils.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TypeInferenceByValuesTests {

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime"/* "js", "java", "c++", "wasm"*/])
    fun testArrayOf(type: String) {
        val code = """
            val tested = arrayOf(1, 2, 3)
            
            fun main() {
                println(tested[1])
            }
        """.trimIndent()

        MultiTest(code)
            .type { Types.Array.withTypeParameter(Types.Int) }
            .runtime { array ->
                val expectedType = Types.Array.withTypeParameter(Types.Int)
                assertEquals(expectedType, array.clazz.type)
                val contents = array.rawValue
                assertInstanceOf<IntArray>(contents)
                assertEquals(listOf(1, 2, 3), contents.toList())
            }
            .compile("1\n")
            .runTest(type)
    }

    // can we enforce const to be fully immutable?
    //  we could only allow it on value-classes of value-classes...

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime"])
    fun testListOf(type: String) {
        val code = """
            val tested = listOf(1, 2, 3)
        """.trimIndent()

        // todo bug:
        //  java.lang.IllegalStateException: Expected return value in zauber.$listOf_o41ki35sgw.fun <V> listOf(vs: zauber.Array<(ro)zauber.Int>): zauber.List<(ro)zauber.Int> to match zauber.List<(ro)zauber.Int>, got zauber.Array<(ro)zauber.Int>
        //    at Test.zbr:9, 39-39, SYMBOL, '='
        //  }
        //  -> why is Array<Int> not a List<Int>??? It clearly says so...

        MultiTest(code)
            .type { Types.List.withTypeParameter(Types.Int) }
            .runtime { value ->
                val type = Types.Array.withTypeParameter(Types.Int)
                assertEquals(runtime.getClass(type), value.clazz)

                val contents = assertInstanceOf<IntArray>(value.rawValue)
                assertEquals(listOf(1, 2, 3), contents.toList())
            }
            .runTest(type)
    }

    @Test
    fun testListOfLambdas() {
        val code = """
            fun runWork(x: Int) = Unit
            val tested = arrayListOf<() -> Unit>(
                { runWork(1) },
                { runWork(2) },
                { runWork(3) },
            )
        """.trimIndent() + YieldTests.stdlib
        testExecute(code)
    }

    @Test
    fun testListOfLambdasTotallyExplicit() {
        val code = """
            fun runWork(x: Int) = Unit
            val tested = arrayListOf<() -> Unit>(
                { -> runWork(1) },
                { -> runWork(2) },
                { -> runWork(3) },
            )
        """.trimIndent() + YieldTests.stdlib
        testExecute(code)
    }

}