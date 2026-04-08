package me.anno.support.python.tokenizer

import me.anno.support.TokenizerBench
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 2300 files, 1100k lines, 40 MB, 6.2M tokens
 * */
class PythonTokenizerBench {
    @Test
    fun testPythonTokenizer() {
        val source = File("/mnt/LinuxGames/Code/cpython")
        TokenizerBench.execute(source, "py") { src, fileName ->
            PythonTokenizer(src, fileName)
                .tokenize().size
        }
    }
}