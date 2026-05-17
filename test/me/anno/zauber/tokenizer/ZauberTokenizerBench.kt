package me.anno.zauber.tokenizer

import me.anno.support.TokenizerBench
import org.junit.jupiter.api.Test
import java.io.File

/**
 * src -> 270 files, 24k lines, 0.9 MB, 160k tokens, 500 ns/t
 * . -> 541 files, 49k lines, 1.7 MB, 280k tokens, 395 ns/t
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