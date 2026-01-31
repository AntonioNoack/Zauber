package me.anno.support.java

import me.anno.support.java.tokenizer.JavaTokenizer
import org.junit.jupiter.api.Test
import java.io.File

// todo instead of scanning tons of random files, we should use specific tests with baselines...
class JavaTokenizerTest {

    var numFiles = 0

    @Test
    fun testJavaTokenizer() {
        val t0 = System.nanoTime()
        val source = File("/mnt/LinuxGames/Code/jdk/src")
        testJavaTokenizer(source)
        val t1 = System.nanoTime()
        println("Tokenized $numFiles files in ${(t1 - t0) / 1e6f} ms")
    }

    private fun testJavaTokenizer(source: File) {
        if (source.isDirectory) {
            for (file in source.listFiles()!!) {
                testJavaTokenizer(file)
            }
        } else if (source.extension == "java") {
            JavaTokenizer(source.readText(), source.name)
                .tokenize()
            numFiles++
        }
    }
}