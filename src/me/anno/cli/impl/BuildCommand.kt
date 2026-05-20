package me.anno.cli.impl

import me.anno.cli.CommandImpl
import me.anno.libraries.Libraries
import me.anno.zauber.ast.rich.parser.ZauberASTClassScanner
import me.anno.zauber.logging.LogManager
import me.anno.zauber.tokenizer.ZauberTokenizer
import java.io.File

object BuildCommand : CommandImpl("build", "b") {

    private val LOGGER = LogManager.getLogger(BuildCommand::class)
    private val zauberExtensions = arrayOf("zauber", "zbr", "kt", "kts")

    override fun execute(options: LinkedHashMap<String, String>, location: File) {
        println("options: $options")
        val project = readFiles(location, scanAll = "test" in options)
        TODO("compile and run")
    }

    override fun printHelp() {
        println("Builds the project or file")
        println("  Options:")
        println("    --run: also run the project")
        println("    --test: also run unit tests; you can specify a specific test, too")
        println("    --target: define which target to compile to, options: [javascript, java, wasm, rust, c++]")
    }

    fun readFiles(location: File, scanAll: Boolean): CompileProject {
        if (location.isDirectory) {
            // try to find project file & load it
            // todo the user could also mean to just run all tests in this directory...
            //  -> scan all folders above, too
            var folder = location
            while (true) {
                val projectFile = File(folder, Libraries.PROJECT_FILE_NAME)
                if (projectFile.exists()) {
                    LOGGER.info("Project File: $projectFile")
                    val project = Libraries.loadProject(projectFile)
                    TODO()
                }
                folder = folder.parentFile
                    ?: break
            }

            if (!scanAll) {
                throw IllegalStateException("Missing project file in $location")
            }

            // todo we still need to find the root file
            //  -> collect all source files, and find the consensus, on what the root file is (2/3 majority)

            val sourceFiles = location.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in zauberExtensions }
                .toList()

            if (sourceFiles.isEmpty()) {
                throw IllegalStateException("Could not find any source files in $location")
            }

            val projectRoots = sourceFiles.map { findProjectRootFromPackage(it) }
            val uniqueRoots = projectRoots.groupBy { it }
            val biggestRoot = uniqueRoots.maxBy { it.value.size }
            if (biggestRoot.value.size < 2f / 3f * projectRoots.size) {
                val candidates = uniqueRoots.entries.sortedByDescending { it.value.size }.take(3)
                throw IllegalStateException(
                    "Project has no consensus on what the root file is, " +
                            "best candidates: ${candidates.map { "${it.key} [${(it.value.size * 100) / projectRoots.size}%]" }}"
                )
            }

            val projectRoot = biggestRoot.key
            LOGGER.info("Project Root: $projectRoot")
            registerFileAsRoot(projectRoot, sourceFiles, )
            TODO()
        } else when (location.extension.lowercase()) {
            in zauberExtensions -> {
                val root = findProjectRootFromPackage(location)
                // todo read using Zauber
                TODO("compile single zauber file, and maybe what it depends on...")
            }
            Libraries.PROJECT_FILE_EXTENSION -> {
                val project = Libraries.loadProject(location)
                TODO("Compile project $project")
            }
            else -> error("Unknown file extension ${location.extension}")
        }
    }

    fun registerFileAsRoot(
        folder: File, sourceFiles: List<File>,
    ) {
        val si = folder.absolutePath.length
        for (file in sourceFiles) {
            val tokenizer = ZauberTokenizer(file.readText(), file.absolutePath.substring(si))
            ZauberASTClassScanner.scanClasses(tokenizer.tokenize())

            // todo scan the file for @Test and fun main(),
            //  and register these functions as entry points
        }
    }

    fun findProjectRootFromPackage(file: File): File {
        file.bufferedReader().use { reader ->
            while (true) {
                val line = reader.readLine()?.trim() ?: break
                if (line.startsWith("package ")) {
                    val i0 = "package ".length
                    var i = i0
                    while (i < line.length && line[i].run { this.isLetterOrDigit() || this == '.' }) {
                        i++
                    }
                    val segments = line.substring(i0, i).split('.')
                    var folder = file
                    for (segment in segments.asReversed()) {
                        if (segment.isEmpty()) continue
                        folder = folder.parentFile
                        check(folder.name.equals(segment, true)) {
                            "Package/folder-name mismatch"
                        }
                    }
                    return folder.parentFile
                }
            }
        }
        LOGGER.warn("Could not find package line in $file, assuming main package")
        return file.parentFile
    }
}