package me.anno.library

import me.anno.libraries.JSONParser
import me.anno.utils.assertEquals
import me.anno.utils.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import kotlin.collections.get

class JSONParserTests {

    @Test
    fun testNull() {
        assertNull(JSONParser.parseJSON("null"))
    }

    @Test
    fun testBooleanTrue() {
        assertEquals(true, JSONParser.parseJSON("true"))
    }

    @Test
    fun testBooleanFalse() {
        assertEquals(false, JSONParser.parseJSON("false"))
    }

    @Test
    fun testInteger() {
        assertEquals(17L, JSONParser.parseJSON("17"))
    }

    @Test
    fun testNegativeInteger() {
        assertEquals(-17L, JSONParser.parseJSON("-17"))
    }

    @Test
    fun testPositiveInteger() {
        assertEquals(17L, JSONParser.parseJSON("+17"))
    }

    @Test
    fun testDouble() {
        assertEquals(17.5, JSONParser.parseJSON("17.5"))
    }

    @Test
    fun testScientificNotation() {
        assertEquals(1e6, JSONParser.parseJSON("1e6"))
        assertEquals(1e-6, JSONParser.parseJSON("1e-6"))
        assertEquals(-1.5e12, JSONParser.parseJSON("-1.5E12"))
    }

    @Test
    fun testSimpleString() {
        assertEquals("hello", JSONParser.parseJSON("\"hello\""))
    }

    @Test
    fun testSingleQuotedString() {
        assertEquals("hello", JSONParser.parseJSON("'hello'"))
    }

    @Test
    fun testEmptyString() {
        assertEquals("", JSONParser.parseJSON("\"\""))
    }

    @Test
    fun testEscapedCharacters() {
        val value = JSONParser.parseJSON(
            "\"a\\nb\\rc\\td\\\\e\\\"f\""
        )

        assertEquals("a\nb\rc\td\\e\"f", value)
    }

    @Test
    fun testUnicodeEscape() {
        val value = JSONParser.parseJSON("\"\\u0041\\u0042\\u0043\"")
        assertEquals("ABC", value)
    }

    @Test
    fun testUnicodeEmoji() {
        val value = JSONParser.parseJSON("\"😀\"")
        assertEquals("😀", value)
    }

    @Test
    fun testUnicodeSurrogatePair() {
        val value = JSONParser.parseJSON(
            "\"\\uD83D\\uDE00\""
        )
        assertEquals("😀", value)
    }

    @Test
    fun testEmptyArray() {
        val array = JSONParser.parseJSON("[]") as List<*>
        assertTrue(array.isEmpty())
    }

    @Test
    fun testSimpleArray() {
        val array = JSONParser.parseJSON("[1,2,3]") as List<*>

        assertEquals(3, array.size)
        assertEquals(1L, array[0])
        assertEquals(2L, array[1])
        assertEquals(3L, array[2])
    }

    @Test
    fun testMixedArray() {
        val array = JSONParser.parseJSON(
            """[1,true,false,null,"hello"]"""
        ) as List<*>

        assertEquals(1L, array[0])
        assertEquals(true, array[1])
        assertEquals(false, array[2])
        assertNull(array[3])
        assertEquals("hello", array[4])
    }

    @Test
    fun testNestedArray() {
        val array = JSONParser.parseJSON(
            """[[1],[2],[3]]"""
        ) as List<*>

        assertEquals(listOf(1L), array[0])
        assertEquals(listOf(2L), array[1])
        assertEquals(listOf(3L), array[2])
    }

    @Test
    fun testEmptyObject() {
        val obj = JSONParser.parseJSON("{}") as Map<*, *>
        assertTrue(obj.isEmpty())
    }

    @Test
    fun testSimpleObject() {
        val obj = JSONParser.parseJSON(
            """{"a":1,"b":2}"""
        ) as Map<*, *>

        assertEquals(1L, obj["a"])
        assertEquals(2L, obj["b"])
    }

    @Test
    fun testUnquotedObjectKeys() {
        val obj = JSONParser.parseJSON(
            """{a:1,b:2}"""
        ) as Map<*, *>

        assertEquals(1L, obj["a"])
        assertEquals(2L, obj["b"])
    }

    @Test
    fun testNestedObject() {
        val obj = JSONParser.parseJSON(
            """
            {
                person: {
                    name: "John",
                    age: 25
                }
            }
            """.trimIndent()
        ) as Map<*, *>

        val person = obj["person"] as Map<*, *>

        assertEquals("John", person["name"])
        assertEquals(25L, person["age"])
    }

    @Test
    fun testWhitespace() {
        val obj = JSONParser.parseJSON(
            """
            
            {
                a : 1 ,
                b : [ 1 , 2 , 3 ]
            }
            
            """.trimIndent()
        ) as Map<*, *>

        assertEquals(1L, obj["a"])
        assertEquals(listOf(1L, 2L, 3L), obj["b"])
    }

    @Test
    fun testTrailingCommaArray() {
        val array = JSONParser.parseJSON("[1,2,3,]") as List<*>

        assertEquals(
            listOf(1L, 2L, 3L),
            array
        )
    }

    @Test
    fun testTrailingCommaObject() {
        val obj = JSONParser.parseJSON(
            """{a:1,b:2,}"""
        ) as Map<*, *>

        assertEquals(1L, obj["a"])
        assertEquals(2L, obj["b"])
    }

    @Test
    fun testComplexDocument() {
        val obj = JSONParser.parseJSON(
            """
            {
                name: "Test",
                enabled: true,
                value: 17.5,
                tags: ["a","b","c"],
                nested: {
                    x: 1,
                    y: 2
                },
                nothing: null
            }
            """.trimIndent()
        ) as Map<*, *>

        assertEquals("Test", obj["name"])
        assertEquals(true, obj["enabled"])
        assertEquals(17.5, obj["value"])
        assertEquals(listOf("a", "b", "c"), obj["tags"])

        val nested = obj["nested"] as Map<*, *>
        assertEquals(1L, nested["x"])
        assertEquals(2L, nested["y"])

        assertNull(obj["nothing"])
    }

    @Test
    fun testInvalidInput() {
        assertThrows<Throwable> {
            JSONParser.readObject("{", 0)
        }
    }

    @Test
    fun testInvalidNumber() {
        assertThrows<Throwable> {
            JSONParser.readNumber("--17", 0)
        }
    }

    @Test
    fun testInvalidString() {
        assertThrows<Throwable> {
            JSONParser.readString("\"abc", 0)
        }
    }

    @Test
    fun testInvalidObject() {
        assertThrows<Throwable> {
            JSONParser.readObject("{a}", 0)
        }
    }

    @Test
    fun testInvalidArray() {
        assertThrows<Throwable> {
            JSONParser.readArray("[1", 0)
        }
    }
}