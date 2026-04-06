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
        // todo allow calling macros with explicit brackets (somehow add the context parameter?)
        val value = testExecute(
            $$"""
            class Sample(var a: Int, var b: Float)
            
            macro GetType(typeName: String, ctx: MacroContext): ClassType {
                return ctx.parse<ClassType>(typeName + "::class")
            }
            
            macro Serialize(input: String, ctx: MacroContext): String {
                // val (fieldName, typeName) = input.split(": ") // <- todo fix this
                val fieldName = input.split(": ")[0]
                val typeName = input.split(": ")[1]
                
                var result = "var r = \"{\"\n"
                val type = GetType!(typeName, ctx)
                for (field in type.fields) {
                    result += "r += \"${field.name}\": \${$fieldName.${field.name}},\n"
                }
                result += "r += \"}\""
                return ctx.parse<String>(result)
            }
            
            fun serialize(sample: Sample) {
                return Serialize"sample: Sample"
            }
            
            val tested = serialize(Sample(1, 2f))
            
            package zauber
            interface List<V> {
                operator fun get(index: Int): V
                operator fun component1(): V = get(0)
                operator fun component2(): V = get(1)
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
                external fun split(separator: String): List<String>
            }
            
            class Field(val name: String, val type: Type)
            class ClassType(val fields: List<Field>)
            
        """.trimIndent()
        )
        assertEquals("<h1>FakeTestMessage!</h1>", value.castToString())
    }
}