package me.anno.support.rust.tokenizer

import me.anno.support.TokenizerBench
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 1700 files, 570k lines, 20 MB, 2.5M tokens, 75 ns/t
 * */
class RustTokenizerBench {
    @Test
    fun testRustTokenizer() {
        val source = File("/mnt/LinuxGames/Code/bevy")
        TokenizerBench.execute(source, "rs") { src, fileName ->
            RustTokenizer(src, fileName)
                .tokenize().size
        }
    }
}