package me.anno.compilation

import me.anno.generation.js.JavaScriptSourceGenerator
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.expansion.DependencyData
import java.io.File

class MinimalJavaScriptCompiler : MinimalCompiler() {
    companion object {
        val minPackageJson by lazy {
            MinimalJavaScriptCompiler::class.java
                .classLoader.getResourceAsStream("./files/package.json")!!
                .readBytes()
        }
    }

    override fun compile(projectFolder: File, srcFolder: File, dependencies: DependencyData, mainMethod: Method) {
        JavaScriptSourceGenerator()
            .generateCode(srcFolder, dependencies, mainMethod)

        // generate a simple Node.js project file
        val pom = File(projectFolder, "package.json")
        pom.writeBytes(minPackageJson)
    }

    override fun execute(projectFolder: File): String {
        return runProcessGetPrinted(projectFolder, "node", "src/main.js")
    }
}