package me.anno.compilation

import me.anno.generation.llvm.LLVMSourceGenerator
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.expansion.DependencyData
import java.io.File

class MinimalLLVMCompiler : MinimalCompiler() {

    override fun compile(projectFolder: File, srcFolder: File, dependencies: DependencyData, mainMethod: Method) {
        val srcFile = File(projectFolder, "Source.ll")
        LLVMSourceGenerator().generateCode(
            srcFile, dependencies,
            mainMethod
        )

        // compile it to assembly
        runProcess(projectFolder, "llc", srcFile.name)

        // todo link it
    }

    override fun execute(projectFolder: File): String {
        TODO("run it")
    }
}