package me.anno.generation.llvmir

import me.anno.generation.java.MinimalJavaCompiler.runProcess
import me.anno.generation.llvm.LLVMSourceGenerator
import me.anno.zauber.dependency.DependencyGraphTests.Companion.printDependencies
import me.anno.zauber.expansion.Dependencies
import me.anno.zauber.resolution.ResolutionUtils
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization
import java.io.File

object MinimalLLVMIRCompiler {

    fun testCompileMainToLLVMAndRun(code: String, registerMethods: () -> Unit): String {
        return testCompileMainToLLVMAndRun(code, false, registerMethods)
    }

    fun testCompileMainToLLVMAndRun(code: String, debug: Boolean, registerMethods: () -> Unit): String {
        val testScope = ResolutionUtils.typeResolveScope(code)
        val method = testScope[ScopeInitType.AFTER_DISCOVERY].methods0.first { it.name == "main" }
        Dependencies.addMethod(MethodSpecialization(method, Specialization.noSpecialization))

        val dependencies = Dependencies.collectClassesAndMethods()
        printDependencies(dependencies)

        // generate LLVM-IR code
        val dstFolder =
            if (debug) File(System.getProperty("user.home"), "Desktop/ZauberLLVM")
            else File.createTempFile("LLVMGenAndRun", ".tmp")

        try {
            dstFolder.delete(); dstFolder.mkdirs()
            val srcFile = File(dstFolder, "Source.ll")

            registerMethods()

            LLVMSourceGenerator.generateCode(
                srcFile, dependencies,
                testScope.methods0.first { it.name == "main" })

            // compile it to assembly
            runProcess(dstFolder, "llc", srcFile.name)

            // todo link it
            // todo run it

            TODO()
        } finally {
            if (!debug) {
                dstFolder.deleteRecursively()
            }
        }
    }
}