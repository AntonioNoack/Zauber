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

        /**
         * nvm is cursed, and cannot be called like any other console command
         * */
        fun runModernNode(nodeVersion: String, fileName: String): Array<String> {
            val command = $$"""
                export NVM_DIR="$HOME/.nvm"
                [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"
                nvm use $$nodeVersion
                node $$fileName
            """.trimIndent()

            return arrayOf(
                "bash",
                "-lc",
                command
            )
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
        return runProcessGetPrinted(projectFolder, *runModernNode("26", "CallWASMFromJS.js"))
    }
}