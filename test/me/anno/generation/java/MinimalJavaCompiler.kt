package me.anno.generation.java

import me.anno.zauber.dependency.DependencyGraphTests.Companion.printDependencies
import me.anno.zauber.expansion.Dependencies
import me.anno.zauber.logging.LogManager
import me.anno.zauber.resolution.ResolutionUtils
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.File
import java.io.InputStream
import kotlin.concurrent.thread

object MinimalJavaCompiler {

    private val LOGGER = LogManager.getLogger(MinimalJavaCompiler::class)

    val minimalPom by lazy {
        MinimalJavaCompiler::class.java
            .classLoader.getResourceAsStream("./files/minimal.pom")!!
            .readBytes()
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
        val jvmProcess = ProcessBuilder(*params)
            .directory(folder)
            .start()
        jvmProcess.errorStream.printToThread(LOGGER::warn)
        val printed = jvmProcess.inputStream.readBytes().decodeToString()
        assertEquals(0, jvmProcess.waitFor()) { "Run(${params.joinToString()}) Failed" }
        return printed
    }


    fun testCompileMainToJavaAndRun(code: String, registerMethods: () -> Unit): String {
        return testCompileMainToJavaAndRun(code, false, registerMethods)
    }

    fun testCompileMainToJavaAndRun(code: String, debug: Boolean, registerMethods: () -> Unit): String {
        val testScope = ResolutionUtils.typeResolveScope(code)
        val method = testScope[ScopeInitType.AFTER_DISCOVERY].methods0.first { it.name == "main" }
        Dependencies.addMethod(MethodSpecialization(method, Specialization.noSpecialization))

        val dependencies = Dependencies.collectClassesAndMethods()
        printDependencies(dependencies)

        // generate Java code
        val javaFolder =
            if (debug) File(System.getProperty("user.home"), "Desktop/ZauberJava")
            else File.createTempFile("JavaGenAndRun", ".tmp")

        try {
            javaFolder.delete(); javaFolder.mkdirs()
            val srcFolder = File(javaFolder, "src").apply { mkdirs() }

            registerMethods()

            JavaSourceGenerator().generateCode(
                srcFolder, dependencies,
                testScope.methods0.first { it.name == "main" })

            // generate simple maven file
            val pom = File(javaFolder, "pom.xml")
            pom.writeBytes(minimalPom)

            // compile it
            runProcess(javaFolder, "mvn", "clean", "install")

            // run it
            val jarFile = File(javaFolder, "target/minimal-1.0-SNAPSHOT.jar")
            return runProcessGetPrinted(javaFolder, "java", "-jar", jarFile.absolutePath)
        } finally {
            if (!debug) {
                javaFolder.deleteRecursively()
            }
        }
    }
}