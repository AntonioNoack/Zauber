package me.anno.zauber

import me.anno.zauber.ast.rich.ZauberASTBuilder
import me.anno.zauber.ast.rich.ASTClassScanner.collectNamedClasses
import me.anno.zauber.expansion.DefaultParameterExpansion.createDefaultParameterFunctions
import me.anno.zauber.expansion.TypeExpansion
import me.anno.zauber.generation.c.CSourceGenerator
import me.anno.zauber.generation.java.JavaSourceGenerator
import me.anno.zauber.logging.LogManager
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.ZauberTokenizer
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Scope
import java.io.File

// we need type resolution, but it is really hard
//  can we outsource the problem?
//  should we try to solve easy problems, first?
//  should we implement an easier language compiler like Java first?
//  can we put all work on the C++/Zig compiler???
//  should we try to create a Kotlin-preprocessor instead?
// -> let's give out best 🌟

// most type resolution probably is easy,
//  e.g. function names usually differ from field names,
//  so once we know all function names known to a scope (or everything), we can already decide many cases
// -> yes/no, it easily gets difficult ^^

// todo inline functions
// todo expand generics
// todo function coloring: async, throws, pure (no side-effects -> comptime)
// todo generate .toString(), .equals() and .hashCode for data classes unless already defined

// if-conditions help us find what type something is...
//  but we need to understand what's possible and how the code flows...
// -> partially done, but early exit remains an issue

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

    // should be the very first thing
    var lastTime = System.nanoTime()

    private val LOGGER = LogManager.getLogger(Compile::class)

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
            val source = ZauberTokenizer(text, fileName).tokenize()
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
            collectNamedClasses(source)
        }
    }

    fun buildASTs() {
        for (i in sources.indices) {
            val source = sources[i]
            ZauberASTBuilder(source, root).readFileLevel()
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        // todo compile and run our samples, and confirm all tests work as expected (write unit tests as samples)

        step("JDK-Overhead") {}

        TypeResolution.doCatchFailures()

        step("Reading & Tokenizing") {
            tokenizeSources()
        }

        step("Indexing Top-Level Classes") {
            collectNamedClassesForTypeResolution()
        }

        step("Parsing ASTs") {
            buildASTs()
        }

        // todo when all expressions are parsed, we can replace more names with being method names / specific fields
        //  -> we could even do that while parsing the AST, because we now have a step before that 🤔

        if (false) printPackages(root, 0)

        step("Creating Default Parameter Functions") {
            createDefaultParameterFunctions(root)
        }

        step("Resolving Types") {
            TypeResolution.resolveTypesAndNames(root)
        }

        step("Resolving Specific Calls") {
            TypeExpansion.resolveSpecificCalls(root)
        }

        if (false) {
            step("Creating C-Code") {
                CSourceGenerator.generateCode(File("./out/c"), root)
            }
        }

        step("Creating Java-Code") {
            JavaSourceGenerator.generateCode(File("./out/java"), root)
        }

        printStats()
    }

    private fun printStats() {
        LOGGER.info("Num Expressions: ${_root_ide_package_.me.anno.zauber.ast.rich.expression.Expression.numExpressionsCreated}")
        // 658k expressions 😲 (1µs/element at the moment)
    }

    fun step(name: String, executeStep: () -> Unit) {
        executeStep()
        val t6 = System.nanoTime()
        LOGGER.info("Took ${((t6 - lastTime) / 1e6f).f3()} ms $name")
        lastTime = t6
    }

    fun Float.f1() = "%.1f".format(this)
    fun Float.f3() = "%.3f".format(this)

    fun printPackages(root: Scope, depth: Int) {
        LOGGER.info("  ".repeat(depth) + root.name)
        for (child in root.children) {
            printPackages(child, depth + 1)
        }
    }

}