package me.anno.support.python.ast

import me.anno.support.TokenizerBench
import me.anno.support.python.tokenizer.PythonTokenizer
import me.anno.zauber.Zauber.root
import org.junit.jupiter.api.Test
import java.io.File

class PythonASTTest {
    @Test
    fun testReadingPython() {
        val source = File("/mnt/LinuxGames/Code/cpython")
        TokenizerBench.execute(source, "py") { src, fileName ->
            val tokens = PythonTokenizer(src, fileName)
                .tokenize()

            tokens.validateBlocks()

            if (fileName.endsWith("_xxtestfuzz/fuzz_pycompile_corpus/input2.py")) {
                // ignored, intentional compiler error
            } else {

                println("Reading $fileName")
                PythonASTBuilder(tokens, root)
                    .readFileLevel()

            }
            tokens.size
        }
    }
}