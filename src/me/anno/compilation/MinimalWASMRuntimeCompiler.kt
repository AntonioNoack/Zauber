package me.anno.compilation

import me.anno.generation.c.CSourceGenerator.Companion.hashMethodParameters
import me.anno.generation.java.JavaSourceGenerator.Companion.nativeJavaNumbers
import me.anno.generation.wasm.WASMSourceGenerator
import me.anno.generation.wasm.runtime.WASMBinaryReader
import me.anno.generation.wasm.runtime.WASMRuntime
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.expansion.DependencyData
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Types
import java.io.File

class MinimalWASMRuntimeCompiler : MinimalCompiler() {

    companion object {
        private val LOGGER = LogManager.getLogger(MinimalWASMRuntimeCompiler::class)
    }

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

        langScope[ScopeInitType.AFTER_DISCOVERY]
        for ((type, _) in nativeJavaNumbers) {

            // register println
            val printScope = langScope.children.firstOrNull {
                val method = it.selfAsMethod
                method != null && method.name == "println" &&
                        method.valueParameters.firstOrNull()?.type == type
            } ?: continue
            val printIntSpec = Specialization(printScope, emptyParameterList())
            runtime.register("zauber_println_${hashMethodParameters(printIntSpec)}") { args ->
                var value = args[1]
                value = when (type) {
                    Types.UInt -> (value as Int).toUInt()
                    Types.ULong -> (value as Long).toULong()
                    else -> value
                }
                printed.append(value).append('\n')
                val unit = runtime.call("obj_zauber_Unit", emptyList())
                listOf(unit)
            }

            // todo register compareTo

        }

        runtime.call("main", emptyList())
        return printed.toString()
    }
}