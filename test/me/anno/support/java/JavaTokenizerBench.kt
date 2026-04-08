package me.anno.support.java

import me.anno.support.TokenizerBench
import me.anno.support.java.tokenizer.JavaTokenizer
import org.junit.jupiter.api.Test
import java.io.File

// todo instead of scanning tons of random files, we should use specific tests with baselines...
/**
 * 14k files, 4200k lines, 169 MB, 15M tokens
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