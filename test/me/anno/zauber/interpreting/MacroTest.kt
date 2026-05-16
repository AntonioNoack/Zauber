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

    // todo we can implement structure-of-arrays using Macros,
    //  so we somehow need to support attaching them to types
    //  @Macro!() or @Macro""
    //  -
    //  class/type info is then put into MacroContext?
    //  macros may add types and functions, so we can only put partial types in there...

    @Test
    fun testParsingXMLAtCompileTime() {
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
class Any
interface List<V> {
    operator fun get(index: Int): V
}

open class Throwable(val message: String)
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
        // todo serialization seems to be correct, but why is the method then returning Unit??

        val sourceCode = $$"""
class Sample(var a: Int, var b: Float)

macro GetType(typeName: String, ctx: MacroContext): ClassType {
    return ctx.parse<ClassType>(typeName + "::class")
}

macro Serialize(input: String, ctx: MacroContext): String {
    val (fieldName, typeName) = input.split(": ")
    
    var result = "var str = \"{\\n\"\n"
    val type = GetType!(typeName)
    for (field in type.fields) {
        result += "str += \"    \\\"${field.name}\\\": \${$fieldName.${field.name}},\\n\"\n"
    }
    result += "str += \"}\"\nstr"
    return ctx.parse<String>(result)
}

fun serialize(sample: Sample) {
    return Serialize"sample: Sample"
}

val tested = serialize(Sample(1, 2f))

package zauber

class Any {
    external open fun toString(): String
}

interface Iterator<V> {
    fun hasNext(): Boolean
    fun next(): V
}

interface Iterable<V> {
    fun iterator(): Iterator<V>
}

interface List<V>: Iterable<V> {
    val size: Int
    operator fun get(index: Int): V
    
    operator fun component1(): V = get(0)
    operator fun component2(): V = get(1)
}

class ArrayIterator<V>(val list: Array<V>): Iterator<V> {
    var index = 0
    fun hasNext() = index < list.size
    fun next(): V = list[index++]
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
class Array<V>(override val size: Int): List<V> {
    external override operator fun get(index: Int): V
    external operator fun set(index: Int, value: V)
    override fun iterator() = ArrayIterator<V>(this)
}

class Int {
    external operator fun plus(other: Int): Int
    external operator fun compareTo(other: Int): Int
    operator fun inc() = this + 1
}

class String {
    external operator fun plus(other: String): String
    operator fun plus(other: Any?): String = this + (other?.toString() ?: "null")
    
    external fun split(separator: String): List<String>
}

class Field(val name: String, val type: Type)
class ClassType<V>(val fields: Array<Field>)

enum class Boolean { TRUE, FALSE }
object Unit
        """.trimIndent()
        val value = testExecute(sourceCode)
        val expectedResult = """
            {
                "a": 1,
                "b": 2.0,
            }
        """.trimIndent()
        assertEquals(expectedResult, value.castToString())
    }
}