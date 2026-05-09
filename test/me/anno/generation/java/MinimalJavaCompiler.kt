package me.anno.generation.java

import me.anno.generation.MinimalCompiler
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.expansion.DependencyData
import java.io.File

object MinimalJavaCompiler : MinimalCompiler() {

    val minimalPom by lazy {
        MinimalJavaCompiler::class.java
            .classLoader.getResourceAsStream("./files/minimal.pom")!!
            .readBytes()
    }

    override fun compile(projectFolder: File, srcFolder: File, dependencies: DependencyData, mainMethod: Method) {
        JavaSourceGenerator().generateCode(srcFolder, dependencies, mainMethod)

        // generate simple maven file
        val pom = File(projectFolder, "pom.xml")
        pom.writeBytes(minimalPom)

        // compile it
        runProcess(projectFolder, "mvn", "clean", "install")
    }

    override fun execute(projectFolder: File): String {
        // run it
        val jarFile = File(projectFolder, "target/minimal-1.0-SNAPSHOT.jar")
        return runProcessGetPrinted(projectFolder, "java", "-jar", jarFile.absolutePath)
    }
}