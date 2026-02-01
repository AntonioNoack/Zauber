package me.anno.support.java

import me.anno.support.java.ast.JavaASTBuilder
import me.anno.support.java.ast.JavaASTClassScanner.Companion.collectNamedJavaClasses
import me.anno.support.java.tokenizer.JavaTokenizer
import me.anno.zauber.Compile.root
import me.anno.zauber.tokenizer.TokenList
import org.junit.jupiter.api.Test
import java.io.File

// todo instead of scanning tons of random files, we should use specific tests with baselines...
class JavaASTBuilderTest {

    private val sources = ArrayList<TokenList>()

    @Test
    fun testJavaTokenizer() {
        val t0 = System.nanoTime()
        val source = File("/mnt/LinuxGames/Code/jdk/src")
        val rootLength = source.absolutePath.length + 1
        testJavaTokenizer(source, rootLength)
        val t1 = System.nanoTime()
        println("Tokenized ${sources.size} files in ${(t1 - t0) / 1e6f} ms")

        sources.add(JavaTokenizer("""
            package zauber;
            class Any
            class Object extends Any
            class StackTraceElement
            interface Iterator<V>
            interface Runnable
            // todo is this properly supported???
            @interface SuppressWarnings
            class Throwable
            class Error extends Throwable
            class InternalError extends Error
            interface WeakHashMap<K, V>
        """.trimIndent(), "helper.java").tokenize())

        for (source in sources) {
            collectNamedJavaClasses(source)
        }

        for (source in sources) {
            println("AST for ${source.fileName}")
            JavaASTBuilder(source, root)
                .readFileLevel()
        }
    }

    private fun testJavaTokenizer(source: File, rootLength: Int) {
        if (source.isDirectory) {
            for (file in source.listFiles()!!) {
                testJavaTokenizer(file, rootLength)
            }
        } else if (source.extension == "java") {
            val fileName = source.absolutePath// .substring(rootLength)
            sources += JavaTokenizer(source.readText(), fileName).tokenize()
        }
    }
}