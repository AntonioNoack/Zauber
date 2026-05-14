package me.anno.zauber.interpreting

import me.anno.utils.assertEquals
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import org.junit.jupiter.api.Test

class RefAndValueTests {

    val stdlib = "\n" + """
        package zauber
        class Ref<V>(var value: V)
        value class Value<V>(val value: V)
        class Any
    """.trimIndent()

    @Test
    fun testReferenceOfValue() {
        val code = """
            value class Vector(val x: Int)
            fun inc(v: Ref<Vector>) {
                v.value.x++
            }
            val tested: Int
                get() {
                    val v = Ref(Vector(1))
                    inc(v)
                    return v.value.x
                }
        """.trimIndent() + stdlib
        assertEquals(2, testExecute(code))
    }

    @Test
    fun testValueOnFieldsMustCopy() {
        // todo this won't work, maybe we need the value-keyword proposal after all
        //  we could solve it by self-defining the .copy() method...
        // todo a macro could be the solution :)
        val code = """
            class Sample(var x: Int)
            fun inc(v: Sample) {
                v.x++
            }
            val tested: Int
                get() {
                    val v = Value(Sample(1))
                    // v must be copied here
                    inc(v.value)
                    return v.value.x
                }
            """.trimIndent() + stdlib
        assertEquals(1, testExecute(code).castToInt())
    }

}