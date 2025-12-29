package me.anno.zauber

import me.anno.zauber.astbuilder.ASTBuilder
import me.anno.zauber.astbuilder.ASTClassScanner.findNamedClasses
import me.anno.zauber.astbuilder.expression.Expression
import me.anno.zauber.expansion.DefaultParameterExpansion.createDefaultParameterFunctions
import me.anno.zauber.expansion.TypeExpansion
import me.anno.zauber.generator.c.CSourceGenerator
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.Tokenizer
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Scope
import java.io.File

// todo we need type resolution, but it is really hard
//  can we outsource the problem?
//  should we try to solve easy problems, first?
//  should we implement an easier language compiler like Java first?
//  can we put all work on the C++/Zig compiler???
//  should we try to create a Kotlin-preprocessor instead?

// todo if-conditions help us find what type something is...
//  but we need to understand what's possible and how the code flows...

// todo most type resolution probably is easy,
//  e.g. function names usually differ from field names,
//  so once we know all function names known to a scope (or everything), we can already decide many cases

// todo expand macros:
//   compile-time if
//   compile-time loop (duplicating instructions)
//   compile-time type replacements??? e.g. float -> double

// todo like Zig, just import .h/.hpp files, and use their types and functions

// todo expand hard generics
// todo dependency & type analysis
// todo build output C++
// todo run C++ compiler

object Compile {

    val stdlib = "zauber"

    val root = Scope("*")

    val sources = ArrayList<TokenList>()

    fun addSource(file: File) {
        addSource(file, file.absolutePath.length + 1, root)
    }

    fun addSource(file: File, rootLen: Int, packageScope: Scope) {
        if (file.isDirectory) {
            val scope =
                if (file.absolutePath.length < rootLen) packageScope
                else packageScope.getOrPut(file.name, null)
            for (child in file.listFiles()!!) {
                addSource(child, rootLen, scope)
            }
        } else if (file.extension == "kt") {
            val text = file.readText()
            val fileName = file.absolutePath.substring(rootLen)
            val source = Tokenizer(text, fileName).tokenize()
            sources.add(source)
            packageScope.sources.add(source)
        }
    }

    fun tokenizeSources() {
        val project = File(".")
        val samples = File(project, "Samples/src")
        val remsEngine = File(project, "../RemsEngine")

        // base: compile itself
        addSource(samples)
        if (true) {
            addSource(File(project, "src"))
        }

        if (false) {
            // bonus: compile Rem's Engine
            addSource(File(remsEngine, "src"))
            addSource(File(remsEngine, "JOML/src"))
            addSource(File(remsEngine, "Bullet/src"))
            addSource(File(remsEngine, "Box2d/src"))
            addSource(File(remsEngine, "Export/src"))
            addSource(File(remsEngine, "Image/src"))
            addSource(File(remsEngine, "JVM/src"))
            addSource(File(remsEngine, "Video/src"))
            addSource(File(remsEngine, "Unpack/src"))
        }
    }

    fun collectNamedClassesForTypeResolution() {
        for (i in sources.indices) {
            val source = sources[i]
            findNamedClasses(source)
        }
    }

    fun buildASTs() {
        for (i in sources.indices) {
            val source = sources[i]
            ASTBuilder(source, root).readFileLevel()
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        // todo compile and run our samples, and confirm all tests work as expected (write unit tests as samples)

        val t0 = System.nanoTime()

        tokenizeSources()
        val t1 = System.nanoTime()
        println("Took ${(t1 - t0) * 1e-6f} ms Reading & Tokenizing")

        collectNamedClassesForTypeResolution()
        val t2 = System.nanoTime()
        println("Took ${(t2 - t1) * 1e-6f} ms Indexing Top-Level Classes")

        buildASTs()
        val t3 = System.nanoTime()
        println("Took ${(t3 - t2) * 1e-6f} ms Parsing AST")

        // todo when all expressions are parsed, we can replace more names with being method names / specific fields

        if (false) printPackages(root, 0)


        createDefaultParameterFunctions(root)
        val t4 = System.nanoTime()
        println("Took ${(t4 - t3) * 1e-6f} ms Parsing AST")


        TypeResolution.resolveTypesAndNames(root)
        val t5 = System.nanoTime()
        println("Took ${(t5 - t4) * 1e-6f} ms Resolving Types")


        TypeExpansion.resolveSpecificCalls(root)
        val t6 = System.nanoTime()
        println("Took ${(t6 - t5) * 1e-6f} ms Resolving Specific Calls")


        println("Num Expressions: ${Expression.numExpressionsCreated}")
        // 658k expressions 😲 (1µs/element at the moment)

        CSourceGenerator.generateCode(File("./out/c"), root)
    }

    fun printPackages(root: Scope, depth: Int) {
        println("  ".repeat(depth) + root.name)
        for (child in root.children) {
            printPackages(child, depth + 1)
        }
    }

}