package me.anno.support.csharp.tokenizer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CSharpTokenizerTests {

    private fun tokenize(src: String) = CSharpTokenizer(src, "Main.cs").tokenize()

    private fun texts(src: String): List<String> {
        val t = tokenize(src)
        return List(t.size) { t.toString(it) }
    }

    @Test
    fun testVerbatimTripleString() {
        val src = "test(@\"\"\"a\nb\"\"\")" // -> "a\nb"
        assertEquals(listOf("test", "(", "@\"\"\"a\nb\"\"\"", ")"), texts(src))
    }

}