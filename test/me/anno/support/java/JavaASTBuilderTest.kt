package me.anno.support.java

import me.anno.support.java.ast.JavaASTBuilder
import me.anno.support.java.ast.JavaASTClassScanner.Companion.collectNamedJavaClasses
import me.anno.support.java.tokenizer.JavaTokenizer
import me.anno.zauber.Compile.root
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.utils.NumberUtils.f1
import org.junit.jupiter.api.Test
import java.io.File
import java.io.OutputStream
import java.io.PrintStream

// todo instead of scanning tons of random files, we should use specific tests with baselines...
class JavaASTBuilderTest {

    private object VoidStream : OutputStream() {
        override fun write(p0: Int) {}
        override fun write(b: ByteArray) {}
        override fun write(b: ByteArray, off: Int, len: Int) {}
    }

    private val sources = ArrayList<TokenList>()

    // 10071 / 14219 -> quite good already, but 30s is also pretty slow...
    //  -> I removed some printing, and now we're down to 10s -> good enough, if we eliminate all printing?
    // todo also, we should make type&name-scanning smarter, very often, it's not a type&name and the type-resolution is expensive

    @Test
    fun testJavaTokenizer() {
        val t0 = System.nanoTime()
        val source = File("/mnt/LinuxGames/Code/jdk/src")
        val rootLength = source.absolutePath.length + 1
        testJavaTokenizer(source, rootLength)
        val t1 = System.nanoTime()
        println("Tokenized ${sources.size} files in ${(t1 - t0) / 1e6f} ms")

        sources.add(
            JavaTokenizer(
                """
            package zauber;
            class Any
            class Object extends Any
            class System
            
            // todo is this properly supported???
            @interface SuppressWarnings
            
            class Throwable
            class StackTraceElement
            
            class Error extends Throwable
            class AssertionError extends Error
            class ExceptionInInitializerError extends Error
            class InternalError extends Error
            
            class Exception extends Throwable
            class ArithmeticException extends Exception
            class IllegalAccessException extends Exception
            class NumberFormatException extends Exception
            class UnsupportedOperationException extends Exception
            
            interface Iterator<V>
            interface Runnable
            interface AutoCloseable
            interface FunctionalInterface
            interface Appendable // for BufferedWriter and such
            interface Comparable<T>
        """.trimIndent(), "helper.java"
            ).tokenize()
        )

        for (source in sources) {
            collectNamedJavaClasses(source)
        }

        val oriOut = System.out
        val oriErr = System.err

        var disabledIO = false
        var success = 0
        for (source in sources) {
            println("AST for ${source.fileName}")
            try {
                JavaASTBuilder(source, root)
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