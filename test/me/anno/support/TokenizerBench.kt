package me.anno.support

import java.io.File
import kotlin.math.max

object TokenizerBench {
    fun execute(
        source: File, extension: String,
        tokenize: (src: String, fileName: String) -> Int
    ) = execute(source, listOf(extension), tokenize)

    fun execute(
        source: File, extensions: List<String>,
        tokenize: (src: String, fileName: String) -> Int
    ) {

        if (!source.exists()) {
            System.err.println("Missing $source, skipping test")
            return
        }

        var numFiles = 0
        var numLines = 0
        var numTokens = 0
        var numBytes = 0

        fun executeOnFile(file: File) {
            if (file.isDirectory) {
                for (child in file.listFiles()!!) {
                    executeOnFile(child)
                }
            } else if (file.extension in extensions) {
                val src = file.readText()
                numTokens += tokenize(src, file.absolutePath)
                numBytes += src.length
                numLines += src.count { it == '\n' } + 1
                numFiles++
            }
        }

        val t0 = System.nanoTime()
        executeOnFile(source)
        val t1 = System.nanoTime()
        println(
            "Tokenized $numFiles files with $numLines lines in " +
                    "${(t1 - t0) / 1e6f} ms, ${(t1 - t0) / max(numTokens, 1)} ns/t, " +
                    "${(numBytes / 1e6f)} MB, $numTokens tokens"
        )
    }
}