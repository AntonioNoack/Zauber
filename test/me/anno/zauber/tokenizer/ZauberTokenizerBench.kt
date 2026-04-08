package me.anno.zauber.tokenizer

import me.anno.support.TokenizerBench
import org.junit.jupiter.api.Test
import java.io.File

/**
 * src -> 270 files, 24k lines, 0.9 MB, 160k tokens, 500 ns/t
 * . -> 410 files, 35k lines, 1.2 MB, 200k tokens, 350 ns/t
 * .. -> 10k files, 940k lines, 33 MB, 3.8M tokens, 60 ns/t -> wow, fast?!?
 * */
class ZauberTokenizerBench {
    @Test
    fun testZauberTokenizer() {
        val source = File(".")
        TokenizerBench.execute(source, "kt") { src, fileName ->
            ZauberTokenizer(src, fileName)
                .tokenize().size
        }
    }
}