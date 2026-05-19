package me.anno.compilation

import me.anno.generation.llvm.LLVMSourceGenerator
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.expansion.DependencyData
import java.io.File

class MinimalLLVMCompiler : MinimalCompiler() {

    override fun compile(projectFolder: File, srcFolder: File, dependencies: DependencyData, mainMethod: Method) {
        val srcFile = File(projectFolder, "Source.ll")
        LLVMSourceGenerator()
            .generateCode(srcFile, dependencies, mainMethod)

        // compile it to assembly
        runProcess(projectFolder, "llc", srcFile.name)
        val assemblyFile = srcFile.nameWithoutExtension + ".s"
        val objectFile = srcFile.nameWithoutExtension + ".o"

        val cFile = "Zauber.c"
        val objectLibFile = "Zauber.o"
        File(projectFolder, cFile)
            .writeText(
                """
                #include <stdio.h>
                void zauber_println_wjpkxu(void* self, int value) {
                    printf("%d\n", value);
                }
            """.trimIndent()
            )

        runProcess(projectFolder, "clang", "-c", assemblyFile, "-o", objectFile)
        runProcess(projectFolder, "clang", "-c", cFile, "-o", objectLibFile)

        // link it
        runProcess(projectFolder, "clang", objectFile, objectLibFile, "-o", "Zauber.a")

    }

    override fun execute(projectFolder: File): String {
        return runProcessGetPrinted(projectFolder, "./Zauber.a")
    }
}