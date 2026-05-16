package me.anno.compilation

import me.anno.generation.java.JavaSourceGenerator
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.expansion.DependencyData
import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider

class MinimalJavaBuildCompiler : MinimalCompiler() {

    companion object {
        val compiler by lazy {
            ToolProvider.getSystemJavaCompiler()
                ?: error("No Java compiler found. Run with JDK, not JRE.")
        }
    }

    override fun compile(projectFolder: File, srcFolder: File, dependencies: DependencyData, mainMethod: Method) {
        JavaSourceGenerator()
            .generateCode(srcFolder, dependencies, mainMethod)

        val classFolder = File(projectFolder, "target")
        classFolder.delete()
        compileJava(srcFolder, classFolder)
        createJar(File(projectFolder, "Zauber.jar"), classFolder)
    }

    override fun execute(projectFolder: File): String {
        // run it
        val jarFile = File(projectFolder, "Zauber.jar")
        return runProcessGetPrinted(projectFolder, "java", "-jar", jarFile.absolutePath)
    }

    private fun compileJava(srcFolder: File, classFolder: File) {
        val sourceFiles = srcFolder.walkTopDown()
            .filter { it.extension == "java" && !it.isDirectory }
            .toList()

        if (sourceFiles.isEmpty()) return

        val args = listOf(
            // 1.8 with source and target is the fastest, even if it prints a few warnings
            "-source", "1.8",
            "-target", "1.8",
           // "--release", "8",
            "-d", classFolder.path
        ) + sourceFiles.map { it.path }

        val result = compiler.run(null, null, null, *args.toTypedArray())
        check(result == 0) {
            error("Compilation failed with exit code $result")
        }
    }

    private fun createJar(jarFile: File, classFolder: File) {
        jarFile.outputStream().buffered().use { fos ->
            JarOutputStream(fos).use { jar ->
                addManifest(jar)
                addDirectory(jar, classFolder)
            }
        }
    }

    private fun addManifest(jar: JarOutputStream) {
        val manifest = java.util.jar.Manifest()
        manifest.mainAttributes.apply {
            put(Attributes.Name.MANIFEST_VERSION, "1.0")
            put(Attributes.Name.MAIN_CLASS, "zauber.LaunchZauber")
            put(Attributes.Name.IMPLEMENTATION_TITLE, "minimal")
            put(Attributes.Name.IMPLEMENTATION_VERSION, "1.0-SNAPSHOT")
        }

        val entry = JarEntry("META-INF/MANIFEST.MF")
        jar.putNextEntry(entry)
        manifest.write(jar)
        jar.closeEntry()
    }

    private fun addDirectory(jar: JarOutputStream, root: File) {
        for (file in root.walkTopDown()) {
            if (file.isDirectory) continue

            val relative = file.toRelativeString(root)
                .replace('\\', '/')
            val entry = JarEntry(relative)

            jar.putNextEntry(entry)
            file.inputStream().use { fileStream ->
                fileStream.transferTo(jar)
            }
            jar.closeEntry()
        }
    }
}