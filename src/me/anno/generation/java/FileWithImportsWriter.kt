package me.anno.generation.java

import me.anno.generation.DeltaWriter
import java.io.File

class FileWithImportsWriter(val self: JavaSourceGenerator, root: File) : DeltaWriter<FileEntry>(root) {

    private val builder = self.builder

    override fun finishContent(v: FileEntry): String {
        check(v.packagePath != "?")
        self.appendPackageDeclaration(v.packagePath)
        self.writeImports(v.imports)

        builder.append(v.content)
        return self.finish()
    }
}