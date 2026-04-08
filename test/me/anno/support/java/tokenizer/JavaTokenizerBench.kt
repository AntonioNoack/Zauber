package me.anno.support.java.tokenizer

import me.anno.support.TokenizerBench
import org.junit.jupiter.api.Test
import java.io.File

// todo instead of scanning tons of random files, we should use specific tests with baselines...

/**
 * 14k files, 4200k lines, 169 MB, 15M tokens, 50 ns/t
 * */
class JavaTokenizerBench {
    @Test
    fun testJavaTokenizer() {
        val source = File("/mnt/LinuxGames/Code/jdk/src")
        TokenizerBench.execute(source, "java") { src, fileName ->
            JavaTokenizer(src, fileName)
                .tokenize().size
        }
    }
}