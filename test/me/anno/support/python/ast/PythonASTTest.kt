package me.anno.support.python.ast

import me.anno.support.TokenizerBench
import me.anno.support.python.tokenizer.PythonTokenizer
import me.anno.utils.StringStyles
import me.anno.utils.StringStyles.style
import me.anno.zauber.Zauber.root
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.ScopeType
import org.junit.jupiter.api.Test
import java.io.File

class PythonASTTest {

    companion object {
        private val LOGGER = LogManager.getLogger(PythonASTTest::class)
    }

    @Test
    fun testReadingPython() {
        val source = File("/mnt/LinuxGames/Code/cpython")

        root.getOrPut("object", ScopeType.NORMAL_CLASS).setEmptyTypeParams()
        root.getOrPut("list", ScopeType.NORMAL_CLASS).setEmptyTypeParams()
        root.getOrPut("RuntimeError", ScopeType.NORMAL_CLASS).setEmptyTypeParams()

        val s0 = source.absolutePath.length + 1
        TokenizerBench.execute(source, "py") { src, fileName ->
            val tokens = PythonTokenizer(src, fileName)
                .tokenize()

            tokens.validateBlocks()

            if (fileName.endsWith("_xxtestfuzz/fuzz_pycompile_corpus/input2.py")) {
                // ignored, intentional compiler error
            } else {

                val relativeFileName = fileName.substring(s0, fileName.length - ".py".length)
                var scope = root
                for (name in relativeFileName.split('/', '\\')) {
                    scope = scope.getOrPut(name, ScopeType.PACKAGE)
                }

                LOGGER.info("Reading ${style(fileName, StringStyles.LIGHT_BLUE)}")
                PythonASTBuilder(tokens, scope)
                    .readFileLevel()

            }
            tokens.size
        }
    }
}