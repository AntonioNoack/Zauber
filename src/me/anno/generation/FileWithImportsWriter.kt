package me.anno.generation

import me.anno.generation.java.JavaSourceGenerator
import java.io.File

class FileWithImportsWriter(val self: JavaSourceGenerator, root: File) : DeltaWriter<FileEntry>(root) {

    private val builder = self.builder

    override fun finishContent(file: File, content: FileEntry): String {
        check(content.packagePath != listOf("?"))
        self.beginPackageDeclaration(content.packagePath, file, content.imports, content.nativeImports)

        if (self.depth > 0) {
            // nicely formatted slow-path
            var i = 0
            val content = content.content
            while (i < content.length) {
                var j = content.indexOf('\n', i)
                if (j < 0) j = content.length
                builder.append(content, i, j)
                self.nextLine()
                i = j + 1
            }
        } else {
            // fast-path
            builder.append(content.content)
        }

        self.endPackageDeclaration(content.packagePath, file)
        return self.finish()
    }
}