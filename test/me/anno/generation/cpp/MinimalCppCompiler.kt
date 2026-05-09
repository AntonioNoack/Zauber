package me.anno.generation.cpp

import me.anno.generation.java.MinimalJavaCompiler.runProcess
import me.anno.generation.java.MinimalJavaCompiler.runProcessGetPrinted
import me.anno.zauber.dependency.DependencyGraphTests.Companion.printDependencies
import me.anno.zauber.expansion.Dependencies
import me.anno.zauber.resolution.ResolutionUtils
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization
import java.io.File

object MinimalCppCompiler {

    val minimalCMakeLists by lazy {
        MinimalCppCompiler::class.java
            .classLoader.getResourceAsStream("./files/CMakeLists.txt")!!
            .readBytes()
    }

    val isLinux: Boolean =
        System.getProperty("os.name")
            .contains("linux", true)

    fun testCompileMainToCppAndRun(code: String, registerMethods: () -> Unit): String {
        return testCompileMainToCppAndRun(code, false, registerMethods)
    }

    fun testCompileMainToCppAndRun(code: String, debug: Boolean, registerMethods: () -> Unit): String {
        val testScope = ResolutionUtils.typeResolveScope(code)
        val method = testScope[ScopeInitType.AFTER_DISCOVERY].methods0.first { it.name == "main" }
        Dependencies.addMethod(MethodSpecialization(method, Specialization.noSpecialization))

        val dependencies = Dependencies.collectClassesAndMethods()
        printDependencies(dependencies)

        // generate code
        val cppFolder =
            if (debug) File(System.getProperty("user.home"), "Desktop/ZauberCpp")
            else File.createTempFile("CppGenAndRun", ".tmp")

        try {
            cppFolder.delete(); cppFolder.mkdirs()
            val srcFolder = File(cppFolder, "src").apply { mkdirs() }

            registerMethods()

            CppSourceGenerator.generateCode(
                srcFolder, dependencies,
                testScope.methods0.first { it.name == "main" })

            File(cppFolder, "CMakeLists.txt")
                .writeBytes(minimalCMakeLists)

            val buildFolder = File(cppFolder, "build")
            buildFolder.mkdirs()

            runProcess(buildFolder, "cmake", "..")
            runProcess(buildFolder, "cmake", "--build", ".")

            // run it
            val executable =
                if (isLinux) File(buildFolder, "Zauber")
                else File(buildFolder, "Debug/Zauber.exe")
            return runProcessGetPrinted(executable)
        } finally {
            if (!debug) {
                cppFolder.deleteRecursively()
            }
        }
    }
}