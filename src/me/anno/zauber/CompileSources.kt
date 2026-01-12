package me.anno.zauber

import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.ZauberASTBuilder
import me.anno.zauber.logging.LogManager
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.ZauberTokenizer
import me.anno.zauber.types.Scope
import java.io.File

object CompileSources {

    private val sources = ArrayList<TokenList>()

    private val LOGGER = LogManager.getLogger(CompileSources::class)

    fun addSource(file: File) {
        addSource(file, file.absolutePath.length + 1, root)
    }

    fun addSource(file: File, rootLen: Int, packageScope: Scope) {
        if (file.isDirectory) {
            val scope =
                if (file.absolutePath.length < rootLen) packageScope
                else packageScope.getOrPut(file.name, null)
            for (child in file.listFiles()!!) {
                addSource(child, rootLen, scope)
            }
        } else if (file.extension == "kt") {
            val text = file.readText()
            val fileName = file.absolutePath.substring(rootLen)
            val source = ZauberTokenizer(text, fileName).tokenize()
            sources.add(source)
            packageScope.sources.add(source)
        }
    }

    fun tokenizeSources(): List<TokenList> {
        val project = File(".")
        val samples = File(project, "Samples/src")
        val remsEngine = File(project, "../RemsEngine")

        // base: compile itself
        addSource(samples)
        if (true) {
            addSource(File(project, "src"))
        }

        if (false) {
            // bonus: compile Rem's Engine
            addSource(File(remsEngine, "src"))
            addSource(File(remsEngine, "JOML/src"))
            addSource(File(remsEngine, "Bullet/src"))
            addSource(File(remsEngine, "Box2d/src"))
            addSource(File(remsEngine, "Export/src"))
            addSource(File(remsEngine, "Image/src"))
            addSource(File(remsEngine, "JVM/src"))
            addSource(File(remsEngine, "Video/src"))
            addSource(File(remsEngine, "Unpack/src"))
        }

        return sources
    }

    fun buildASTs() {
        for (i in sources.indices) {
            val source = sources[i]
            val language =
                if (source.fileName.endsWith(".kt", true)) ZauberLanguage.KOTLIN
                else ZauberLanguage.ZAUBER
            ZauberASTBuilder(source, root, language).readFileLevel()
        }
    }

    fun printPackages(root: Scope, depth: Int) {
        LOGGER.info("  ".repeat(depth) + root.name)
        for (child in root.children) {
            printPackages(child, depth + 1)
        }
    }

}