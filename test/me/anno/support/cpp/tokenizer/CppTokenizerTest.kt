package me.anno.support.cpp.tokenizer

import me.anno.support.TokenizerBench
import org.junit.jupiter.api.Test
import java.io.File

// todo instead of scanning tons of random files, we should use specific tests with baselines...
/**
 * 37k files, 6056k lines, 242 MB, 41M tokens
 * */
class CppTokenizerTest {

    private val extensions = "cpp,hpp,c,h".split(',')

    @Test
    fun testCppTokenizer() {
        val source = File("/mnt/LinuxGames/Code/boost/libs")
        TokenizerBench.execute(source, extensions) { src, fileName ->
            CppTokenizer(src, fileName, false)
                .tokenize().size
        }
    }
}