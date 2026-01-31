package me.anno.support.cpp.preprocessor

import me.anno.support.cpp.preprocessor.Preprocessor
import me.anno.support.cpp.tokenizer.CppTokenizer
import me.anno.zauber.tokenizer.TokenList
import org.junit.jupiter.api.Assertions

abstract class PreprocessorTestBase {

    protected fun preprocess(source: String, fileName: String): TokenList {
        return preprocess(mapOf(fileName to source), fileName)
    }

    protected fun preprocess(files: Map<String, String>, fileName: String): TokenList {
        val tokenized = files.mapValues { (fileName, source) ->
            CppTokenizer(source, fileName, true).tokenize()
        }
        /*println("Tokenized:")
        for ((fileName, tokens) in tokenized) {
            println("  '$fileName': ${tokens.toDebugString()}")
        }*/
        val pp = Preprocessor(tokenized) { _, name -> name }
        return pp.preprocess(fileName)
    }

    protected fun assertTokens(source: String, expected: String) {
        val tokens = preprocess(source, "test.c")
        Assertions.assertEquals(
            normalize(expected),
            normalize(tokens.toDebugString())
        )
    }

    private fun normalize(s: String): String =
        s.trim().replace(Regex("\\s+"), " ")

}