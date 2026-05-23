package me.anno.compilation

import me.anno.generation.jvm.JVMBytecodeGenerator
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.expansion.DependencyData
import java.io.File

/**
 * Compile a project into a .jar file.
 * This skips the intermediate .java (and .class on disk) files, skipping the Java compiler.
 *
 * We for one, are faster, because we skip the Java compiler, but also, because we can skip converting the graph into if-else-code.
 * */
class MinimalJVMCompiler : MinimalCompiler() {

    override fun compile(projectFolder: File, srcFolder: File, dependencies: DependencyData, mainMethod: Method) {
        JVMBytecodeGenerator()
            .generateCode(projectFolder, dependencies, mainMethod)
    }

    override fun execute(projectFolder: File): String {
        return runProcessGetPrinted(projectFolder, "java", "-jar", "./Zauber.jar")
    }
}