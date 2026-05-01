package me.anno.generation.java

import me.anno.zauber.dependency.DependencyGraphTests.Companion.printDependencies
import me.anno.zauber.expansion.Dependencies
import me.anno.zauber.logging.LogManager
import me.anno.zauber.resolution.ResolutionUtils
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.io.InputStream
import kotlin.concurrent.thread

class JavaGenAndRunTest {

    companion object {

        private val LOGGER = LogManager.getLogger(JavaGenAndRunTest::class)

        val minimalPom by lazy {
            JavaGenAndRunTest::class.java
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
    }

    @Test
    fun testGenAndRun() {

        LogManager.disableLoggers(
            "TypeResolution,Inheritance,ASTSimplifier," +
                    "CallExpression," +
                    "ConstructorResolver," +
                    "MethodResolver,ResolvedMethod," +
                    "FieldExpression,,Field,ResolvedField,FieldResolver," +
                    "MemberResolver,"
        )

        val code = """
            val x = 1 + 2
            fun main() {
                println(x)
            }
            
            package zauber
            class Any
            object Unit
            class Int {
                external operator fun plus(other: Int): Int
            }
            
            external fun println(line: Int)
        """.trimIndent()

        val testScope = ResolutionUtils.typeResolveScope(code)
        val method = testScope[ScopeInitType.AFTER_DISCOVERY].methods0.first { it.name == "main" }
        Dependencies.addMethod(MethodSpecialization(method, Specialization.noSpecialization))

        val dependencies = Dependencies.collectClassesAndMethods()
        printDependencies(dependencies)

        // generate Java code
        val javaFolder =
            if (true) File(System.getProperty("user.home"), "Desktop/ZauberJava")
            else File.createTempFile("JavaGenAndRun", ".tmp")
        javaFolder.delete(); javaFolder.mkdirs()//; javaFolder.deleteOnExit()
        val srcFolder = File(javaFolder, "src").apply { mkdirs() }
        JavaSourceGenerator.generateCode(srcFolder, dependencies)

        // generate simple maven file
        val pom = File(javaFolder, "pom.xml")
        pom.writeBytes(minimalPom)

        // todo generate meta-file...

        // compile it
        runProcess(javaFolder, "mvn", "clean", "install")

        // run it
        val jarFile = File(javaFolder, "target/minimal-1.0-SNAPSHOT.jar")
        runProcess(javaFolder, "java", "-jar", jarFile.absolutePath)
    }


}