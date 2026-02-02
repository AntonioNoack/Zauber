package me.anno.support.csharp

import me.anno.support.csharp.tokenizer.CSharpTokenizer
import org.junit.jupiter.api.Test
import java.io.File

// todo instead of scanning tons of random files, we should use specific tests with baselines...
class CSharpTokenizerTest {

    var numFiles = 0
    var numBytes = 0L

    @Test
    fun testCSharpTokenizer() {
        val t0 = System.nanoTime()
        val source = File("/mnt/LinuxGames/Code/runtime/src")
        testCSharpTokenizer(source)
        val t1 = System.nanoTime()
        println("Tokenized $numFiles files in ${(t1 - t0) / 1e6f} ms, ${(numBytes / 1e6f)} MB")
    }

    private fun testCSharpTokenizer(source: File) {
        if (source.isDirectory) {
            for (file in source.listFiles()!!) {
                testCSharpTokenizer(file)
            }
        } else if (source.extension == "cs") {
            val text = source.readText()
            CSharpTokenizer(text, source.name)
                .tokenize()
            numFiles++
            numBytes += text.length
        }
    }
}