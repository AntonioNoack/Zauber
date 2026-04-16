package me.anno.support.jvm

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.Instance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JVMInstructionTests {

    abstract class TestClass {
        abstract fun call(): Any
    }

    fun <V : TestClass> test(sample: V): Instance {
        // stdlib
        testExecute(
            """
val tested = 0 // unused

package zauber
class Int {
    external operator fun plus(other: Int): Int
    external operator fun minus(other: Int): Int
    external operator fun mul(other: Int): Int
    external operator fun div(other: Int): Int
    external operator fun compareTo(other: Int): Int
    external fun equals(other: Int): Boolean
    external fun toByte(): Byte
    external fun toChar(): Char
    external fun shl(bits: Int): Int
    external fun shr(bits: Int): Int
    external fun ushr(bits: Int): Int
    external fun and(other: Int): Int
    external fun or(other: Int): Int
    external fun xor(other: Int): Int
    operator fun negate() = 0 - this
    external fun mod(other: Int): Int
}
class Array<V>(val size: Int) {
    external operator fun set(index: Int, value: V)
    external operator fun set(index: Int, value: Any)
    external operator fun set(index: Int, value: Byte)
    external operator fun get(index: Int): V
}
    """.trimIndent()
        )

        val sampleClass = sample.javaClass.name
            .replace('.', '/')

        FirstJVMClassReader.getScope(sampleClass, null)
            .methods.first { it.name == "call" }
            .scope.scope

        return testExecute(
            """
        import java.util.ArrayList
        import ${
                sampleClass
                    .replace('/', '.')
                    .replace('$', '.')
            }
        
        val tested = ${sample.javaClass.simpleName}().call()
    """.trimIndent(), reset = false
        )
    }

    class FloatToInt : TestClass() {
        override fun call(): Int = 5f.toInt()
    }

    @Test
    fun testFloatToInt() {
        assertEquals(1, test(FloatToInt()).castToInt())
    }
}