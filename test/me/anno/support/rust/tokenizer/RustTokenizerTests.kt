package me.anno.support.rust.tokenizer

import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RustTokenizerTests {

    private fun tokenize(src: String): TokenList {
        return RustTokenizer(src, "test.rs").tokenize()
    }

    private fun TokenList.tokens(): List<Pair<TokenType, String>> {
        return List(size) { i -> getType(i) to toString(i) }
    }

    private fun TokenList.types() = List(size) { i -> getType(i) }
    private fun TokenList.strings() = List(size) { i -> toString(i) }

    @Test
    fun testIdentifiers() {
        val t = tokenize("abc _foo bar123")
        assertEquals(
            listOf("abc", "_foo", "bar123"),
            t.strings()
        )
    }

    @Test
    fun testKeywords() {
        val t = tokenize("fn let mut struct return").types()
        assertTrue(t.all { it == TokenType.KEYWORD }) {
            "Expected all tokens to be keywords, got $t"
        }
    }

    @Test
    fun testMixedKeywordsAndNames() {
        val t = tokenize("fn foo let bar")
        assertEquals(TokenType.KEYWORD, t.getType(0))
        assertEquals(TokenType.NAME, t.getType(1))
        assertEquals(TokenType.KEYWORD, t.getType(2))
        assertEquals(TokenType.NAME, t.getType(3))
    }

    @Test
    fun testSymbolsMerged() {
        val t = tokenize("a==b != c <= d >= e")
        val symbols = t.tokens().filter { it.first == TokenType.SYMBOL }
        assertTrue(symbols.any { it.second == "==" })
        assertTrue(symbols.any { it.second == "!=" })
        assertTrue(symbols.any { it.second == "<=" })
        assertTrue(symbols.any { it.second == ">=" })
    }

    @Test
    fun testSingleSymbols() {
        val t = tokenize("+-*/")
        assertEquals(listOf("+-*/"), t.tokens().map { it.second })
    }

    @Test
    fun testDelimiters() {
        val t = tokenize("(){},;[]")
        val types = t.types()

        assertTrue(types.contains(TokenType.OPEN_CALL))
        assertTrue(types.contains(TokenType.CLOSE_CALL))
        assertTrue(types.contains(TokenType.OPEN_BLOCK))
        assertTrue(types.contains(TokenType.CLOSE_BLOCK))
        assertTrue(types.contains(TokenType.OPEN_ARRAY))
        assertTrue(types.contains(TokenType.CLOSE_ARRAY))
        assertTrue(types.contains(TokenType.COMMA))
        assertTrue(types.contains(TokenType.SEMICOLON))
    }

    @Test
    fun testLineComment() {
        val t = tokenize("let a // hello\nb")
        assertEquals(1, t.numComments)
    }

    @Test
    fun testBlockComment() {
        val t = tokenize("a /* comment */ b")
        assertEquals(1, t.numComments)
    }

    @Test
    fun testNestedBlockComment() {
        val t = tokenize("a /* outer /* inner */ outer */ b")
        assertEquals(1, t.numComments)
    }

    @Test
    fun testSimpleString() {
        val t = tokenize("\"hello\"")
        assertEquals(TokenType.STRING, t.getType(0))
        assertEquals("\"hello\"", t.toString(0))
    }

    @Test
    fun testEscapedString() {
        val t = tokenize("\"a\\nb\"")
        assertEquals(TokenType.STRING, t.getType(0))
    }

    @Test
    fun testUnterminatedString() {
        val t = tokenize("\"abc")
        assertEquals(TokenType.STRING, t.getType(0))
    }

    @Test
    fun testRawStringSimple() {
        val t = tokenize("r\"abc\"")
        assertEquals(TokenType.STRING, t.getType(0))
    }

    @Test
    fun testRawStringWithHashes() {
        val t = tokenize("r#\"abc\"#")
        assertEquals(TokenType.STRING, t.getType(0))
    }

    @Test
    fun testRawStringMultipleHashes() {
        val t = tokenize("r###\"abc\"###")
        assertEquals(TokenType.STRING, t.getType(0))
    }

    @Test
    fun testInvalidRawStringFallsBackToName() {
        val t = tokenize("r#abc")
        assertEquals(TokenType.NAME, t.getType(0))
    }

    @Test
    fun testDecimalNumber() {
        val t = tokenize("12345")
        assertEquals(TokenType.NUMBER, t.getType(0))
    }

    @Test
    fun testFloatNumber() {
        val t = tokenize("12.34")
        assertEquals(TokenType.NUMBER, t.getType(0))
    }

    @Test
    fun testNumberWithUnderscores() {
        val t = tokenize("1_000_000")
        assertEquals(TokenType.NUMBER, t.getType(0))
    }

    @Test
    fun testHexNumber() {
        val t = tokenize("0xFF")
        assertEquals(TokenType.NUMBER, t.getType(0))
    }

    @Test
    fun testBinaryNumber() {
        val t = tokenize("0b1010")
        assertEquals(TokenType.NUMBER, t.getType(0))
    }

    @Test
    fun testOctalNumber() {
        val t = tokenize("0o77")
        assertEquals(TokenType.NUMBER, t.getType(0))
    }

    @Test
    fun testNumberWithSuffix() {
        val t = tokenize("123u32")
        assertEquals(TokenType.NUMBER, t.getType(0))
    }

    @Test
    fun testLifetime() {
        val t = tokenize("'a")
        assertEquals(TokenType.NAME, t.getType(0))
    }

    @Test
    fun testLifetimeLong() {
        val t = tokenize("'abc123")
        assertEquals(TokenType.NAME, t.getType(0))
    }

    @Test
    fun testCharLiteral() {
        val t = tokenize("'x'")
        assertEquals(TokenType.NUMBER, t.getType(0))
    }

    @Test
    fun testEscapedCharLiteral() {
        val t = tokenize("'\\n'")
        assertEquals(TokenType.NUMBER, t.getType(0))
    }

    @Test
    fun testComplexRustSnippet() {
        val code = """
            fn main() {
                let x: i32 = 10;
                let s = r#"hello"#;
                let c = 'a';
                // comment
            }
        """.trimIndent()

        val t = tokenize(code).types()
        assertTrue(TokenType.KEYWORD in t)
        assertTrue(TokenType.STRING in t)
        assertTrue(TokenType.NUMBER in t)
    }

    @Test
    fun testSymbolEdgeCases() {
        val t = tokenize("a..b ..= c -> d")
        val symbols = t.tokens().filter { it.first == TokenType.SYMBOL }.map { it.second }
        assertTrue(symbols.contains(".."))
        assertTrue("->" in symbols)
    }

    @Test
    fun testWhitespaceHandling() {
        val t = tokenize("a   b\nc\t d")
        assertEquals(4, t.size)
    }

    @Test
    fun testEmptyInput() {
        val t = tokenize("")
        assertEquals(0, t.size)
    }
}