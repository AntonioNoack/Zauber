package me.anno.compilation

import me.anno.generation.rust.RustSourceGenerator
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.expansion.DependencyData
import java.io.File

open class MinimalRustCompiler(val preserveFolder: Boolean = false) :
    MinimalCompiler() {

    companion object {
        val cargoToml by lazy {
            MinimalRustCompiler::class.java
                .classLoader.getResourceAsStream("./files/Cargo.toml")!!
                .readBytes()
        }
    }

    override fun compile(
        projectFolder: File, srcFolder: File,
        dependencies: DependencyData, mainMethod: Method
    ) {
        // avoid deleting build folder, because Rust builds dependencies
        val projectFolder = if (preserveFolder) File(projectFolder.parentFile, "ZauberRust") else projectFolder
        val srcFolder = if (preserveFolder) File(projectFolder, "src") else srcFolder
        if (preserveFolder) srcFolder.mkdirs()

        RustSourceGenerator()
            .generateCode(srcFolder, dependencies, mainMethod)

        File(projectFolder, "Cargo.toml")
            .writeBytes(cargoToml)
    }

    override fun execute(projectFolder: File): String {
        val projectFolder = if (preserveFolder) File(projectFolder, "../ZauberRust") else projectFolder
        return runProcessGetPrinted(projectFolder, "cargo", "run")
    }
}