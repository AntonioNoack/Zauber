package me.anno.compilation

import me.anno.utils.ResolutionUtils
import me.anno.utils.ResolutionUtils.printDependencies
import me.anno.utils.assertEquals
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.expansion.Dependencies
import me.anno.zauber.expansion.DependencyData
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.types.Specialization
import java.io.File
import java.io.InputStream
import kotlin.concurrent.thread

/**
 * base class for toy compilers,
 * compiling Zauber to other languages
 * */
abstract class MinimalCompiler {

    companion object {

        private val LOGGER = LogManager.getLogger(MinimalCompiler::class)

        val isLinux: Boolean =
            System.getProperty("os.name")
                .contains("linux", true)
    }

    fun InputStream.printToThread(showLine: (String) -> Unit) {
        thread {
            val reader = bufferedReader()
            while (true) {
                val line = reader.readLine() ?: break
                showLine(line)
            }
        }
    }

    fun runProcess(folder: File, vararg params: String) {
        val jvmProcess = ProcessBuilder(*params)
            .directory(folder)
            .start()
        jvmProcess.inputStream.printToThread(LOGGER::info)
        jvmProcess.errorStream.printToThread(LOGGER::warn)
        assertEquals(0, jvmProcess.waitFor()) { "Run(${params.joinToString()}) Failed" }
    }

    fun runProcessGetPrinted(folder: File, vararg params: String): String {
        check(params.isNotEmpty()) { "Cannot run empty command" }
        val jvmProcess = ProcessBuilder(*params)
            .directory(folder)
            .start()
        jvmProcess.errorStream.printToThread(LOGGER::warn)
        val printed = jvmProcess.inputStream.readBytes().decodeToString()
        assertEquals(0, jvmProcess.waitFor()) { "Run(${params.joinToString()}) Failed" }
        return printed
    }

    fun testCompileMainAndRun(code: String, registerMethods: () -> Unit): String {
        return testCompileMainAndRun(code, false, registerMethods)
    }

    fun testCompileMainAndRun(code: String, debug: Boolean, registerMethods: () -> Unit): String {

        LOGGER.info("Starting compilation")

        val testScope = ResolutionUtils.typeResolveScope(code)
        val method = testScope[ScopeInitType.AFTER_DISCOVERY].methods0.first { it.name == "main" }
        Dependencies.addMethod(Specialization(method.memberScope, emptyParameterList()))

        val dependencies = Dependencies.collectClassesAndMethods()
        printDependencies(dependencies)

        // generate Java code
        val projectFolder =
            if (debug) File(System.getProperty("user.home"), "Desktop/Zauber")
            else File.createTempFile("GenAndRun", ".tmp")

        try {
            projectFolder.deleteRecursively()
            projectFolder.mkdirs()

            val srcFolder = File(projectFolder, "src")
            if (srcFolder.exists()) srcFolder.deleteRecursively()
            srcFolder.mkdirs()

            registerMethods()

            val mainMethod = testScope.methods0.first { it.name == "main" }
            compile(projectFolder, srcFolder, dependencies, mainMethod)

            return execute(projectFolder)
        } finally {
            if (!debug) {
                projectFolder.deleteRecursively()
            }
        }
    }

    abstract fun execute(projectFolder: File): String
    abstract fun compile(
        projectFolder: File, srcFolder: File,
        dependencies: DependencyData, mainMethod: Method
    )
}