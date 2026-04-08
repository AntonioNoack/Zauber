package me.anno.support.csharp.tokenizer

import me.anno.support.TokenizerBench
import org.junit.jupiter.api.Test
import java.io.File

// todo instead of scanning tons of random files, we should use specific tests with baselines...
/**
 * 31k files, 8.1M lines, 430 MB, 66.9M tokens
 * */
class CSharpTokenizerBench {
    @Test
    fun testCSharpTokenizer() {
        val source = File("/mnt/LinuxGames/Code/runtime/src")
        TokenizerBench.execute(source, "cs") { src, fileName ->
            try {
                CSharpTokenizer(src, fileName)
                    .tokenize().size
            } catch (e: IllegalStateException) {
                e.printStackTrace()
                -1
            }
        }
    }
}