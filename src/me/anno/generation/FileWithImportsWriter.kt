package me.anno.generation

import me.anno.generation.java.JavaSourceGenerator
import java.io.File

class FileWithImportsWriter(val self: JavaSourceGenerator, root: File) : DeltaWriter<FileEntry>(root) {

    private val builder = self.builder

    override fun finishContent(file: File, content: FileEntry): String {
        check(content.packagePath != listOf("?"))
        self.beginPackageDeclaration(content.packagePath, file, content.imports, content.nativeImports)
        builder.append(content.content)
        self.endPackageDeclaration(content.packagePath, file)
        return self.finish()
    }
}