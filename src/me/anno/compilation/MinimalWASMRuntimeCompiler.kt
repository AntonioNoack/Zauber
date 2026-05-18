package me.anno.compilation

import me.anno.generation.wasm.WASMSourceGenerator
import me.anno.generation.wasm.runtime.WASMBinaryReader
import me.anno.generation.wasm.runtime.WASMRuntime
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.expansion.DependencyData
import java.io.File

class MinimalWASMRuntimeCompiler : MinimalCompiler() {

    override fun compile(projectFolder: File, srcFolder: File, dependencies: DependencyData, mainMethod: Method) {

        val wasmTextFile = File(projectFolder, "Source.wat")
        val wasmBinaryFile = File(projectFolder, "Binary.wasm")

        val gen = WASMSourceGenerator()
        gen.generateCode(wasmTextFile, dependencies, mainMethod)
        gen.binary.out.writeTo(wasmBinaryFile)
    }

    override fun execute(projectFolder: File): String {
        val wasmBinaryFile = File(projectFolder, "Binary.wasm")
        val binary = WASMBinaryReader(wasmBinaryFile.readBytes()).read()
        val runtime = WASMRuntime(binary)
        val printed = StringBuilder()
        runtime.register("zauber_println_wjpkxu") { args ->
            val pNumber = args[1] as Int
            printed.append(pNumber).append('\n')
            val unit = runtime.call("obj_zauber_Unit", emptyList())
            listOf(unit)
        }
        runtime.call("main", emptyList())
        return printed.toString()
    }
}