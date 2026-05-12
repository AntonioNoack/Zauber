package me.anno.compilation

import me.anno.generation.wasm.WASMSourceGenerator
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.expansion.DependencyData
import java.io.File

class MinimalWASMCompiler(val directlyBinary: Boolean = false) : MinimalCompiler() {

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

        val wasmBinaryFile = File(projectFolder, "Binary.wasm")

        if (directlyBinary) {

            WASMSourceGenerator(true)
                .generateCode(wasmBinaryFile, dependencies, mainMethod)

        } else {

            val wasmTextFile = File(projectFolder, "Source.wat")
            WASMSourceGenerator(false)
                .generateCode(wasmTextFile, dependencies, mainMethod)

            // compile it to WASM binary
            runProcess(projectFolder, "wat2wasm", wasmTextFile.name, "-o", wasmBinaryFile.name)

        }
    }

    override fun execute(projectFolder: File): String {
        return runProcessGetPrinted(projectFolder, "node", "CallWASMFromJS.js")
    }
}