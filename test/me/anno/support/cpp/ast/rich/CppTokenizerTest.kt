package me.anno.support.cpp.ast.rich

import me.anno.support.cpp.tokenizer.CppTokenizer
import org.junit.jupiter.api.Test
import java.io.File

// todo instead of scanning tons of random files, we should use specific tests with baselines...
class CppTokenizerTest {

    private val extensions = "cpp,hpp,c,h".split(',')

    var numFiles = 0
    var numBytes = 0L

    @Test
    fun testCppTokenizer() {
        val t0 = System.nanoTime()
        val source = File("/mnt/LinuxGames/Code/boost/libs")
        testCppTokenizer(source)
        val t1 = System.nanoTime()
        println("Tokenized $numFiles files in ${(t1 - t0) / 1e6f} ms, ${(numBytes / 1e6f)} MB")
    }

    private fun testCppTokenizer(source: File) {
        if (source.isDirectory) {
            for (file in source.listFiles()!!) {
                testCppTokenizer(file)
            }
        } else if (source.extension in extensions) {
            val text = source.readText()
            CppTokenizer(text, source.name, false)
                .tokenize()
            numFiles++
            numBytes += text.length
        }
    }
}