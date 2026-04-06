package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
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
                return ctx.parse<XMLNode>(
                    "XMLNode(\"h1\")\n" +
                        ".addContent(\"FakeTestMessage!\")"
                )
            }
            
            val xmlNode = XML"<h1>Hello World!</h1>"
            val tested = xmlNode.toString()
            
            package zauber
            interface List<V> {
                operator fun get(index: Int): V
            }
            
            class Throwable(val message: String)
            object MacroContext: Throwable("") {
                lateinit var result: String
                external fun mark(i0: Int, i1: Int, type: String)
                fun <R> parse(tokens: String): R {
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
        assertEquals("<h1>FakeTestMessage!</h1>", value.castToString())
    }

    @Test
    fun testCreatingSerializerAtCompileTime() {
        // todo executing macros inside macros isn't supported yet:
        //  we cannot call a macro with explicit arguments, because call it with strings by default...
        //  two types of calling a macro, with runtime, and const-time data?

        // todo allow calling macros with explicit brackets (somehow add the context parameter?)
        //  and allow them having multiple arguments?... just split by comma(?)
        //  and context is added (if missing...?)
        val sourceCode = this::class.java.classLoader
            .getResourceAsStream("me/anno/zauber/interpreting/MacroSerializer.zbr")!!
            .readBytes().decodeToString()
        val value = testExecute(sourceCode)
        val expectedResult = """
            {
                "a": 0,
                "b": 2.0,
            }
        """.trimIndent()
        assertEquals(expectedResult, value.castToString())
    }
}