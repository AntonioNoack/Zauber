package me.anno.support.csharp

import me.anno.support.csharp.ast.CSharpASTBuilder
import me.anno.support.csharp.ast.CSharpASTClassScanner.Companion.collectNamedCSharpClasses
import me.anno.support.csharp.tokenizer.CSharpTokenizer
import me.anno.zauber.Compile.root
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.utils.NumberUtils.f1
import org.junit.jupiter.api.Test
import java.io.File
import java.io.OutputStream
import java.io.PrintStream

// todo instead of scanning tons of random files, we should use specific tests with baselines...
class CSharpASTBuilderTest {

    private object VoidStream : OutputStream() {
        override fun write(p0: Int) {}
        override fun write(b: ByteArray) {}
        override fun write(b: ByteArray, off: Int, len: Int) {}
    }

    private val sources = ArrayList<TokenList>()

    @Test
    fun testCSharpTokenizer() {
        val t0 = System.nanoTime()
        val source = File("/mnt/LinuxGames/Code/runtime/src")
        val rootLength = source.absolutePath.length + 1
        testCSharpTokenizer(source, rootLength)
        val t1 = System.nanoTime()
        println("Tokenized ${sources.size} files in ${(t1 - t0) / 1e6f} ms")

        sources.add(
            CSharpTokenizer(
                """
            namespace zauber {
                class Fact // @Test from xUnit
            }
        """.trimIndent(), "helper.cs"
            ).tokenize()
        )

        for (source in sources) {
            // println("Collecting types in ${source.fileName}")
            collectNamedCSharpClasses(source)
        }

        val oriOut = System.out
        val oriErr = System.err

        var disabledIO = false
        var success = 0
        for (source in sources) {
            println("AST for ${source.fileName}")
            try {
                CSharpASTBuilder(source, root)
                    .readFileLevel()
                success++
            } catch (e: Throwable) {
                if (!disabledIO) {
                    e.printStackTrace()
                    System.setOut(PrintStream(VoidStream))
                    System.setErr(PrintStream(VoidStream))
                    disabledIO = true
                }
            }
        }

        System.setOut(oriOut)
        System.setErr(oriErr)

        check(success == sources.size) {
            "Success: $success / ${sources.size}, ${(success * 100f / sources.size).f1()}%"
        }
    }

    private fun testCSharpTokenizer(source: File, rootLength: Int) {
        if (source.isDirectory) {
            for (file in source.listFiles()!!) {
                testCSharpTokenizer(file, rootLength)
            }
        } else if (source.extension == "cs") {
            val fileName = source.absolutePath
            sources += CSharpTokenizer(source.readText(), fileName).tokenize()
        }
    }
}