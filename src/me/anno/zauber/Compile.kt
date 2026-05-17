package me.anno.zauber

import me.anno.utils.NumberUtils.f3
import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.CompileSources.buildASTs
import me.anno.zauber.CompileSources.printPackages
import me.anno.zauber.CompileSources.tokenizeSources
import me.anno.zauber.ast.rich.parser.ZauberASTClassScanner.Companion.scanAllClasses
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.typeresolution.TypeResolution

// todo convert JVM Bytecode AST into simplified AST...
//  what about generics? we can either keep them generic, or specialize them... both would be good...
//  generally, we should make specialization optional... union super-types will always be specialized

// todo make variable capture by lambdas explicit:
//  mark mutable fields as captured;
//  mutable fields then need some sort of wrapper in the method

// todo at compile-time define types???
// todo collect field names & visibility flags at collectNames-time? would allow for immediate name resolution for first names of chains
// todo make any field const-able; if a field is const:
//  - it must be computable from just that expression
//  - and other const values
//  - comptime exact maths?
//  - allow file IO?
//  - allow method calls
//  - execute with specializations ofc

// todo expand macros:
//   compile-time if
//   compile-time loop (duplicating instructions)
//   compile-time type replacements??? e.g. float -> double

// todo like Zig, just import .h/.hpp files, and use their types and functions

object Compile {

    // should be the very first thing
    private var lastTime = System.nanoTime()
    private val startTime = lastTime

    private val LOGGER = LogManager.getLogger(Compile::class)

    const val STDLIB_NAME = "zauber"
    val root by threadLocal {
        Scope("*").apply {
            // ensure zauber is a package
            getOrPut(STDLIB_NAME, ScopeType.PACKAGE).apply {
                setEmptyTypeParams()
            }
        }
    }

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
            scanAllClasses(sources)
        }

        step("Parsing ASTs") {
            buildASTs()
        }

        if (false) printPackages(root, 0)

        // todo find out where/what our main method/entry-points is ...
        /* val method = testScope[ScopeInitType.AFTER_DISCOVERY].methods0.first { it.name == "main" }
         Dependencies.addMethod(MethodSpecialization(method, Specialization.noSpecialization))

         val (classes, methods) = Dependencies.collectClassesAndMethods()
         printDependencies(classes, methods)

         step("Resolution & Creating Java-Code") {
             JavaSourceGenerator.generateCode(File("./out/java"), root)
         }*/

        printStats()
    }

    private fun printStats() {
        LOGGER.info("Num Lines: ${CompileSources.numLines}")
        LOGGER.info("Num Expressions: ${Expression.numExpressionsCreated}")
        val endTime = System.nanoTime()
        LOGGER.info("Took ${(endTime - startTime) / 1e6f} ms in total")
    }

    private fun <R> step(name: String, executeStep: () -> R): R {
        val result = executeStep()
        val currTime = System.nanoTime()
        LOGGER.info("Took ${((currTime - lastTime) / 1e6f).f3()} ms $name")
        lastTime = currTime
        return result
    }
}