package me.anno.support.python.tokenizer

import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PythonTokenizerTest {

    private fun tokenize(src: String) = PythonTokenizer(src, "main.py").tokenize()

    private fun types(src: String): List<TokenType> {
        val t = tokenize(src)
        return List(t.size) { t.getType(it) }
    }

    private fun texts(src: String): List<String> {
        val t = tokenize(src)
        return List(t.size) { t.toString(it) }
    }

    private fun assertContains(t: TokenList, type: TokenType) {
        assertTrue((0 until t.size).any { t.getType(it) == type }) {
            "Missing $t in $t"
        }
    }

    @Test
    fun testIdentifiers() {
        val src = "abc _x x1 __init__"
        assertEquals(
            listOf("abc", "_x", "x1", "__init__"),
            texts(src)
        )
    }

    @Test
    fun testKeywords() {
        val src = "if else while return class def"
        val t = tokenize(src)
        for (i in 0 until t.size) {
            assertEquals(TokenType.KEYWORD, t.getType(i))
        }
    }

    @Test
    fun testMixedNamesAndKeywords() {
        val src = "ifx if"
        val t = tokenize(src)
        assertEquals(TokenType.NAME, t.getType(0))
        assertEquals(TokenType.KEYWORD, t.getType(1))
    }

    @Test
    fun testIntegers() {
        assertEquals(listOf("123"), texts("123"))
    }

    @Test
    fun testFloat() {
        assertEquals(listOf("3.14"), texts("3.14"))
    }

    @Test
    fun testScientificNotation() {
        assertEquals(listOf("1e10", "2.5e-3"), texts("1e10 2.5e-3"))
    }

    @Test
    fun testHexBinaryOctal() {
        assertEquals(
            listOf("0xFF", "0b1010", "0o77"),
            texts("0xFF 0b1010 0o77")
        )
    }

    @Test
    fun testUnderscoreNumbers() {
        assertEquals(listOf("1_000_000"), texts("1_000_000"))
    }

    @Test
    fun testSimpleString() {
        assertEquals(listOf("\"hello\""), texts("\"hello\""))
    }

    @Test
    fun testSingleQuoteString() {
        assertEquals(listOf("'hello'"), texts("'hello'"))
    }

    @Test
    fun testEscapedString() {
        assertEquals(listOf("\"a\\nb\""), texts("\"a\\nb\""))
    }

    @Test
    fun testTripleString() {
        assertEquals(
            listOf("\"\"\"hello\nworld\"\"\""),
            texts("\"\"\"hello\nworld\"\"\"")
        )
    }

    @Test
    fun testComment() {
        val t = tokenize("x # comment")
        assertEquals(TokenType.NAME, t.getType(0))
    }

    @Test
    fun testSymbols() {
        val src = "( ) [ ] { } , ; ."
        val expected = listOf(
            TokenType.OPEN_CALL,
            TokenType.CLOSE_CALL,
            TokenType.OPEN_ARRAY,
            TokenType.CLOSE_ARRAY,
            TokenType.OPEN_BLOCK,
            TokenType.CLOSE_BLOCK,
            TokenType.COMMA,
            TokenType.SEMICOLON,
            TokenType.SYMBOL
        )
        assertEquals(expected, types(src))
    }

    @Test
    fun testDotNumber() {
        assertEquals(listOf(".5"), texts(".5"))
    }

    @Test
    fun testIndentation() {
        val src = """
            a
                b
            c
        """.trimIndent()

        assertTrue(types(src).contains(TokenType.INDENT))
        assertTrue(types(src).contains(TokenType.DEDENT))
    }

    @Test
    fun testMultipleDedents() {
        val src = """
            a
                b
                    c
            d
        """.trimIndent()

        val types = types(src)
        assertTrue(types.count { it == TokenType.DEDENT } >= 2)
    }

    @Test
    fun testEmptyLinesIgnoredInIndent() {
        val src = """
            a

                b
        """.trimIndent()
        val types = tokenize(src)
        assertContains(types, TokenType.INDENT)
    }

    @Test
    fun testFStringSimple() {
        val src = """f"hello {x}""""
        val types = tokenize(src)
        assertContains(types, TokenType.APPEND_STRING)
        assertContains(types, TokenType.OPEN_CALL)
        assertContains(types, TokenType.CLOSE_CALL)
    }

    @Test
    fun testFStringExpression() {
        val src = """f"{1+2}""""
        val texts = texts(src)
        assertTrue(texts.any { it == "1" }) { "Expected 1 in ${tokenize(src)}" }
        assertTrue(texts.any { it == "2" }) { "Expected 2 in ${tokenize(src)}" }
    }

    @Test
    fun testFStringEscapedBraces() {
        val src = """f"{{}}" """
        val texts = texts(src)
        assertTrue(texts.any { it.contains("{{") || it.contains("}}") })
    }

    @Test
    fun testNestedFStringExpression() {
        val src = """f"{foo(bar(1))}""""
        val texts = texts(src)
        assertTrue(texts.contains("foo")) { "Expected foo in $texts" }
        assertTrue(texts.contains("bar")) { "Expected bar in $texts" }
    }

    @Test
    fun testFStringWithTextAndExpr() {
        val src = """f"Hello {name}!" """
        val types = tokenize(src)
        assertContains(types, TokenType.STRING)
        assertContains(types, TokenType.APPEND_STRING)
    }

    @Test
    fun testStringPrefixes() {
        val src = """r"raw" b"bytes" u"unicode""""
        val texts = texts(src)
        assertEquals(3, texts.size) {
            "Expected 3 tokens, got ${tokenize(src)}"
        }
    }

    @Test
    fun testOperatorsAsSymbols() {
        val src = "+ - * / == != <= >="
        val types = types(src)
        assertTrue(types.all { it == TokenType.SYMBOL })
    }

    @Test
    fun testComplexPythonSnippet() {
        val src = """
            def foo(x):
                if x > 10:
                    return f"value={x}"
                else:
                    return None
        """.trimIndent()

        val types = tokenize(src)
        assertContains(types, TokenType.KEYWORD)
        assertContains(types, TokenType.INDENT)
        assertContains(types, TokenType.DEDENT)
        assertContains(types, TokenType.STRING)
    }

    @Test
    fun testUnclosedStringDoesNotCrash() {
        val src = "\"abc"
        assertDoesNotThrow { tokenize(src) }
    }

    @Test
    fun testUnclosedFStringDoesNotCrash() {
        val src = """f"{abc""""
        assertDoesNotThrow { tokenize(src) }
    }

    @Test
    fun testDeepNestingFString() {
        val src = """f"{f'{x}'}""""
        assertDoesNotThrow { tokenize(src) }
    }

    @Test
    fun testLargeInput() {
        val src = buildString {
            repeat(1000) {
                append("x = $it\n")
            }
        }
        assertDoesNotThrow {
            tokenize(src)
        }
    }
}