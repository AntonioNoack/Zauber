package me.anno.zauber

import me.anno.zauber.CompileSources.buildASTs
import me.anno.zauber.CompileSources.printPackages
import me.anno.zauber.CompileSources.tokenizeSources
import me.anno.zauber.ast.rich.ASTClassScanner.collectNamedClassesForTypeResolution
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.expansion.DefaultParameterExpansion.createDefaultParameterFunctions
import me.anno.zauber.expansion.TypeSpecialization
import me.anno.zauber.generation.java.JavaSourceGenerator
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Scope
import me.anno.zauber.utils.NumberUtils.f3
import java.io.File

// todo inline functions
// todo expand generics
// todo function coloring: async, throws, yields, uses-non-inline-lambdas, pure (no side-effects -> comptime)
// todo generate .toString(), .equals() and .hashCode for data classes unless already defined

// todo expand macros:
//   compile-time if
//   compile-time loop (duplicating instructions)
//   compile-time type replacements??? e.g. float -> double

// todo like Zig, just import .h/.hpp files, and use their types and functions

object Compile {

    // should be the very first thing
    private var lastTime = System.nanoTime()

    private val LOGGER = LogManager.getLogger(Compile::class)

    val stdlib = "zauber"

    val root = Scope("*")

    @JvmStatic
    fun main(args: Array<String>) {
        // todo compile and run our samples, and confirm all tests work as expected (write unit tests as samples)

        step("JDK-Overhead") {}

        // if (false)
        TypeResolution.doCatchFailures()

        val sources = step("Reading & Tokenizing") {
            tokenizeSources()
        }

        step("Indexing Top-Level Classes") {
            collectNamedClassesForTypeResolution(sources)
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
            TypeSpecialization.specializeAllGenerics(root)
        }

        step("Creating Java-Code") {
            JavaSourceGenerator.generateCode(File("./out/java"), root)
        }

        printStats()
    }

    private fun printStats() {
        LOGGER.info("Num Expressions: ${Expression.numExpressionsCreated}")
        // 658k expressions 😲 (1µs/element at the moment)
    }

    private fun <R> step(name: String, executeStep: () -> R): R {
        val result = executeStep()
        val t6 = System.nanoTime()
        LOGGER.info("Took ${((t6 - lastTime) / 1e6f).f3()} ms $name")
        lastTime = t6
        return result
    }
}