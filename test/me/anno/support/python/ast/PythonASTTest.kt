package me.anno.support.python.ast

import me.anno.support.TokenizerBench
import me.anno.support.python.tokenizer.PythonTokenizer
import me.anno.zauber.Compile.root
import org.junit.jupiter.api.Test
import java.io.File

class PythonASTTest {
    @Test
    fun testReadingPython() {
        val source = File("/mnt/LinuxGames/Code/cpython")
        TokenizerBench.execute(source, "py") { src, fileName ->

            println("Reading $fileName")

            val tokens = PythonTokenizer(src, fileName)
                .tokenize()

            tokens.validateBlocks()

            PythonASTBuilder(tokens, root)
                .readFileLevel()

            tokens.size
        }
    }
}