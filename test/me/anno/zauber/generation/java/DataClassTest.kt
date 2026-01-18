package me.anno.zauber.generation.java

import me.anno.zauber.generation.java.JavaGenerationTest.Companion.testClassGeneration
import org.junit.jupiter.api.Test

class DataClassTest {
    @Test
    fun testGeneratesDataClass() {
        val source = $$"""
            data class Test(val x: String) {
                /*fun toString(): String {
                    return "Test(x=$x)"
                }*/
            }
            package zauber
            class Any {
                external fun toString(): String
                external fun hashCode(): Int
            }
            class String {
                external fun plus(other: String): String
            }
        """.trimIndent()
        val generated = testClassGeneration(source)
        Thread.sleep(250)
        println(generated)
    }

}