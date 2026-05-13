package me.anno.compilation

import me.anno.generation.wasm.WASMSourceGenerator
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.expansion.DependencyData
import java.io.File

class MinimalWASMCompiler : MinimalCompiler() {

    companion object {
        val minimalJS by lazy {
            MinimalRustCompiler::class.java
                .classLoader.getResourceAsStream("./files/CallWASMFromJS.js")!!
                .readBytes()
        }
    }

    override fun compile(projectFolder: File, srcFolder: File, dependencies: DependencyData, mainMethod: Method) {

        File(projectFolder, "CallWASMFromJS.js")
            .writeBytes(minimalJS)

        val wasmTextFile = File(projectFolder, "Source.wat")
        val wasmBinaryFile = File(projectFolder, "Binary.wasm")

        val gen = WASMSourceGenerator()
        gen.generateCode(wasmTextFile, dependencies, mainMethod)
        gen.binary.out.writeTo(wasmBinaryFile)
    }

    override fun execute(projectFolder: File): String {
        return runProcessGetPrinted(projectFolder, "node", "CallWASMFromJS.js")
    }
}