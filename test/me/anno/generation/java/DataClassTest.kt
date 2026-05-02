package me.anno.generation.java

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
                external fun equals(other: String): Boolean
            }
        """.trimIndent()
        val generated = GenerationTest.testClassGenIsFine(source)
        Thread.sleep(250)
        println(generated)
    }

}