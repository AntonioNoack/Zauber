package me.anno.compilation

import me.anno.generation.python.PythonSourceGenerator
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.expansion.DependencyData
import java.io.File

class MinimalPythonCompiler : MinimalCompiler() {

    override fun compile(projectFolder: File, srcFolder: File, dependencies: DependencyData, mainMethod: Method) {
        PythonSourceGenerator()
            .generateCode(srcFolder, dependencies, mainMethod)
    }

    override fun execute(projectFolder: File): String {
        return runProcessGetPrinted(projectFolder, "python3", "src/main.py")
    }
}