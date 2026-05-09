package me.anno.generation.cpp

import me.anno.generation.MinimalCompiler
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.expansion.DependencyData
import java.io.File

object MinimalCppCompiler : MinimalCompiler() {

    val minimalCMakeLists by lazy {
        MinimalCppCompiler::class.java
            .classLoader.getResourceAsStream("./files/CMakeLists.txt")!!
            .readBytes().decodeToString()
    }

    val cppStandardLib by lazy {
        MinimalCppCompiler::class.java
            .classLoader.getResourceAsStream("./files/CppStandardLib.hpp")!!
            .readBytes()
    }

    override fun compile(
        projectFolder: File, srcFolder: File,
        dependencies: DependencyData, mainMethod: Method
    ) {
        val gen = CppSourceGenerator()
        gen.generateCode(srcFolder, dependencies, mainMethod)

        val si = projectFolder.absolutePath.length + 1
        val filesList = gen.cppFiles.joinToString("\n") { file -> file.absolutePath.substring(si) }

        File(projectFolder, "CMakeLists.txt")
            .writeText(minimalCMakeLists.replace("CPP_FILES_LIST", filesList))

        File(srcFolder, "CppStandardLib.hpp")
            .writeBytes(cppStandardLib)

        val buildFolder = File(projectFolder, "build")
        buildFolder.mkdirs()

        runProcess(buildFolder, "cmake", "..")
        runProcess(buildFolder, "cmake", "--build", ".")
    }

    override fun execute(projectFolder: File): String {
        val buildFolder = File(projectFolder, "build")
        val programName =
            if (isLinux) "./Zauber"
            else "./Debug/Zauber.exe"
        return runProcessGetPrinted(buildFolder, programName)
    }
}