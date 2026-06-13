package me.anno.zauber.interpreting

import me.anno.utils.MultiTest
import me.anno.utils.assertEquals
import me.anno.zauber.types.Types
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * many compile targets like Java don't support having multiple fields of the same name in a function,
 * this shall check that we make them unique
 * */
class SameNameTests {
    @ParameterizedTest
    @ValueSource(strings = ["runtime", "js", "java", "c++", "c", "wasm", "llvm", "python"])
    fun testSameFieldNameInMethod(type: String) {
        val code = """
            fun test(i: Int): Int {
                var i = i + 1
                if (i > 0) {
                    val i = i + 2
                    return i
                }
                return i
            }
            val tested = test(5)
            
            package zauber
            class Any
            external class Int {
                external operator fun plus(other: Int): Int
                external operator fun compareTo(other: Int): Int
            }
            class Array<V>(override val size: Int) {
                external fun set(index: Int, value: V)
            }
            external fun println(arg0: Int)
            enum class Boolean { TRUE, FALSE }
        """.trimIndent()
        MultiTest(code)
            .type { Types.Int }
            .runtime { value -> assertEquals(8, value.castToInt()) }
            .compile("8\n")
            .runTest(type)
    }
}