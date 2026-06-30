package me.anno.compilation

import me.anno.generation.c.CSourceGenerator
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.expansion.DependencyData
import java.io.File

open class MinimalCCompiler :
    MinimalCompiler(null) {

    companion object {
        val minimalCMakeListsForC by lazy {
            MinimalCCompiler::class.java
                .classLoader.getResourceAsStream("files/CMakeLists-C.txt")!!
                .readBytes().decodeToString()
        }

        val cStandardLib by lazy {
            MinimalCCompiler::class.java
                .classLoader.getResourceAsStream("files/CStandardLib.h")!!
                .readBytes()
        }
    }

    override fun compile(
        projectFolder: File, srcFolder: File,
        dependencies: DependencyData, mainMethod: Method
    ) {
        val gen = CSourceGenerator()
        gen.generateCode(srcFolder, dependencies, mainMethod)

        val si = projectFolder.absolutePath.length + 1
        val filesList = gen.cppFiles.joinToString("\n") { file ->
            file.absolutePath.substring(si)
        }

        File(projectFolder, "CMakeLists.txt")
            .writeText(
                minimalCMakeListsForC
                    .replace("FILES_LIST", filesList)
                    .replace(
                        "FIND_PACKAGES",
                        libraries.entries.joinToString("\n") { (name, _) ->
                            "find_package($name REQUIRED)"
                        })
                    .replace(
                        "LINK_WITH_LIBRARIES",
                        "target_link_libraries(Zauber PRIVATE $nativeLibraries${
                            libraries.values.flatMap { it.linkNames }.distinct()
                                .joinToString("") { name -> "\n$name" }
                        })"
                    )
            )

        File(srcFolder, "CStandardLib.h")
            .writeBytes(cStandardLib)

        val buildFolder = File(projectFolder, "build")
        buildFolder.mkdirs()

        runProcess(buildFolder, "cmake", "..")
        runProcess(buildFolder, "cmake", "--build", ".")
    }

    private val libraries = LinkedHashMap<String, Library>()
    private var nativeLibraries = "m" // m = Math

    private class Library(val name: String, val linkNames: List<String>)

    fun addCMakeLibrary(name: String, vararg libraryNames: String) {
        libraries[name] = Library(name, libraryNames.asList())
    }

    override fun execute(projectFolder: File): String {
        val buildFolder = File(projectFolder, "build")
        val programName =
            if (isLinux) "./Zauber"
            else "./Debug/Zauber.exe"
        return runProcessGetPrinted(buildFolder, programName)
    }
}