package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.FieldGetSetTest.Companion.assertContains
import me.anno.zauber.interpreting.FieldGetSetTest.Companion.assertThrowsCheck
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * implement that name + string = comptime call to name(string),
 *   and then the returned list of strings is tokenized and evaluated,
 *   and its result is used
 * */
class MacroTest {
    @Test
    fun testParsingXMLAtCompileTime() {
        // todo implement a proper small XML parser?
        //  needs ArrayList-implementation, too...
        val value = testExecute(
            $$"""
            class XMLNode(val type: String) {
                var content = ""
                fun addContent(text: String): XMLNode {
                    content += text
                    return this
                }
                
                fun toString(): String {
                    return "<$type>$content</$type>"
                }
            }
            
            macro XML(input: String, ctx: MacroContext): XMLNode {
                return ctx.parse<XMLNode>(listOf(
                    "XMLNode", "(", "\"h1\"", ")",
                        ".", "addContent", "(", "\"Hello World!\"", ")"
                ))
            }
            
            val xmlNode = XML"<h1>Hello World!</h1>"
            val tested = xmlNode.toString()
            
            package zauber
            interface List<V> {
                operator fun get(index: Int): V
            }
            
            class Throwable(val message: String)
            object MacroContext: Throwable("") {
                lateinit var result: List<String>
                external fun mark(i0: Int, i1: Int, type: String)
                fun <R> parse(tokens: List<String>): R {
                    result = tokens
                    throw this
                }
            }
            
            fun <V> listOf(vararg v: V) = v
            class Array<V>(val size: Int): List<V> {
                external override operator fun get(index: Int): V
                external operator fun set(index: Int, value: V)
            }
            
            class String {
                external operator fun plus(other: String): String
            }
        """.trimIndent()
        )
        assertEquals("<h1>Hello World!</h1>", value.castToString())
    }
}