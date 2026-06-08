package me.anno.support.cpp.execution

import me.anno.support.cpp.ast.rich.CppASTBuilder
import me.anno.support.cpp.ast.rich.CppStandard
import me.anno.support.cpp.preprocessor.Preprocessor
import me.anno.support.cpp.tokenizer.CppTokenizer
import me.anno.zauber.Zauber.root
import org.junit.jupiter.api.Test
import java.io.File

class StbImageTests {

    val header = File("/mnt/LinuxGames/Code/simple-c/stb_image.h")

    @Test
    fun testReadingImageInInterpreter() {
        // load C/C++ code as Zauber
        val files = mapOf(
            header.name to header.readText(),
            "stdio.h" to "",
            "stdlib.h" to """
                typedef struct {
                    char* fileName;
                } FILE;
            """.trimIndent(),
        )
        val tokenized = files.mapValues { (fileName, source) ->
            CppTokenizer(source, fileName, isCNotCxx = true).tokenize()
        }

        val pp = Preprocessor(tokenized) { _, name -> name }
        val tokens = pp.preprocess(header.name)

        CppASTBuilder(tokens, root, CppStandard.C11).readFileLevel()
        val scope = root

        // todo define standard library functions
        // todo execute it
    }

    @Test
    fun testReadingImageWhenCompilingToC() {
        // todo just load it as available bindings,
        //  then use it when actually executing code
    }
}