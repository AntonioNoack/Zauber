package me.anno.cli.impl

import me.anno.cli.CommandImpl
import me.anno.compilation.*
import me.anno.libraries.Libraries
import me.anno.libraries.Libraries.PROJECT_FILE_EXTENSION
import me.anno.libraries.Library
import me.anno.support.Language
import me.anno.zauber.Zauber.root
import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.ast.rich.expression.resolved.ThisExpression
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.ast.rich.parser.ZauberASTClassScanner
import me.anno.zauber.expansion.DependencyData
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.tokenizer.ZauberTokenizer
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import java.io.File
import java.net.URI

object BuildCommand : CommandImpl("build", "b") {

    private val LOGGER = LogManager.getLogger(BuildCommand::class)
    private val zauberExtensions = arrayOf("zauber", "zbr", "kt", "kts")

    override fun execute(options: Options, location: File) {
        println("options: $options")
        val project = readFiles(location, scanAll = "test" in options, options)
        val compiler = when (options["target"]) {
            "java" -> MinimalJavaBuildCompiler()
            "jvm" -> MinimalJVMCompiler()
            "js" -> MinimalJavaScriptCompiler()
            "python" -> MinimalPythonCompiler()
            "c++" -> MinimalCppCompiler()
            "llvm" -> MinimalLLVMCompiler()
            "wasm" -> MinimalWASMCompiler()
            "rust" -> MinimalRustCompiler()
            "runtime" -> null
            else -> {
                if ("run-only" in options || "test-only" in options) null
                else throw IllegalStateException("You must specify a target")
            }
        }
        // compile
        if (compiler != null) {
            val projectFolder = project.root
            val data = DependencyData()
            val main = project.mainMethod
            val srcFolder = File(projectFolder, "generated")
            compiler.compile(projectFolder, srcFolder, data, main)
            if ("run" in options) compiler.execute(projectFolder)
        }
        if ("test" in options) {
            TODO("compile and run unit tests")
        }
    }

    override fun printHelp() {
        println("Builds the project or file")
        println("  Options:")
        println("    --run: also run the project")
        println("    --test: also run unit tests; you can specify a specific test, too")
        println("    --target: define which target to compile to, options: [javascript, java, wasm, rust, c++]")
    }

    // todo load dependencies, too...

    fun readFiles(location: File, scanAll: Boolean, options: Options): CompileProject {
        if (location.isDirectory) {
            // try to find project file & load it
            // the user could also mean to just run all tests in this directory...
            //  -> scan all folders above, too; todo if testing, limit the methods to folders inside
            var folder = location
            while (true) {
                val projectFile = File(folder, Libraries.PROJECT_FILE_NAME)
                if (projectFile.exists()) {
                    LOGGER.info("Project File: $projectFile")
                    val project = Libraries.loadProject(projectFile)
                    val root = extractFileFromURI(project.source)
                    val sourceFiles = collectSourceFiles(root)
                    return registerFilesAndFindMain(root, sourceFiles, options, project)
                }
                folder = folder.parentFile
                    ?: break
            }

            if (!scanAll) {
                throw IllegalStateException("Missing project file in $location")
            }

            // we still need to find the root file
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
            return registerFilesAndFindMain(projectRoot, sourceFiles, options, null) // dependencies unknown
        } else when (location.extension.lowercase()) {
            in zauberExtensions -> {
                val root = findProjectRootFromPackage(location)
                options["main"] = location.absolutePath.substring(root.absolutePath.length + 1)
                val config = root.listFiles()!!.firstOrNull { it.extension == PROJECT_FILE_EXTENSION }
                if (config != null) return readFiles(config, scanAll, options) // for dependencies

                val sourceFiles = collectSourceFiles(root)
                return registerFilesAndFindMain(root, sourceFiles, options, null) // dependencies unknown
            }
            PROJECT_FILE_EXTENSION -> {
                val project = Libraries.loadProject(location)
                val root = extractFileFromURI(project.source)
                val sourceFiles = collectSourceFiles(root)
                return registerFilesAndFindMain(root, sourceFiles, options, project)
            }
            else -> error("Unknown file extension ${location.extension}")
        }
    }

    fun collectSourceFiles(root: File): List<File> {
        return root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in zauberExtensions }
            .toList()
    }

    fun extractFileFromURI(uri: URI?): File {
        check(uri != null) { "Missing project source file" }
        when (uri.scheme) {
            "file" -> return File(uri.path)
            "jar" -> TODO("'Extract' jar")
            else -> TODO("Unknown scheme ${uri.scheme}")
        }
    }

    fun registerFilesAndFindMain(
        root: File, sourceFiles: List<File>, options: Options,
        project: Library?
    ): CompileProject {
        val si = root.absolutePath.length
        for (file in sourceFiles) {
            val fileName = file.absolutePath.substring(si)
            val tokenizer = ZauberTokenizer(file.readText(), fileName)
            val tokens = tokenizer.tokenize()
            val scanner = ZauberASTClassScanner(tokens, Language.byFileName(file.name))
            scanner.readFileLevel()
        }

        if (project != null && project.dependencies.isNotEmpty()) {
            TODO("Load files from dependencies")
        }

        val methods = findEntryPoints()
        val mainMethod = selectMainMethod(methods, options)
        val unitTests = methods.filter { method -> method.name != "main" || isUnitTest(method) }
        return CompileProject(root, mainMethod, unitTests)
    }

    fun selectMainMethod(methods: List<Method>, options: Options): Method {
        val candidates = methods.filter { method -> method.name == "main" && method.ownerScope.isObjectLike() }
        val main = options["main"]
        if (main != null) {
            val matches = candidates.filter { it.ownerScope.pathStr == main }
            check(matches.isNotEmpty()) {
                "No matching main method found, candidates: ${matches.map { it.ownerScope.pathStr }}"
            }
            check(matches.size == 1) {
                "Main method is ambiguous: $matches"
            }
            return matches.first()
        } else {
            if (candidates.isEmpty()) {
                if ("only-test" in options) {
                    return createEmptyMainMethod()
                } else {
                    throw IllegalStateException("No main method found")
                }
            }
            check(candidates.size == 1) {
                "Please specify which main-method to execute, got $candidates"
            }
            return candidates.first()
        }
    }

    fun findEntryPoints(): List<Method> {
        val result = ArrayList<Method>()
        findEntryPoints(root, result)
        return result
    }

    fun createEmptyMainMethod(): Method {
        val ownerScope = langScope
        val scope = ownerScope.getOrPut("main", ScopeType.METHOD)
        val method = Method(
            null, false, "main",
            emptyList(), emptyList(), scope, Types.Unit,
            emptyList(), ThisExpression(Types.Unit.clazz, scope, -1),
            Flags.SYNTHETIC, -1
        )
        scope.selfAsMethod = method
        return method
    }

    /**
     * scan the file for @Test and fun main()
     * */
    fun findEntryPoints(scope: Scope, result: ArrayList<Method>) {

        scope[ScopeInitType.AFTER_DISCOVERY]

        if (scope.isClassLike()) {
            for (child in scope.children) {
                findEntryPoints(child, result)
            }
        } else {
            val asMethod = scope.selfAsMethod
            if (asMethod != null) {
                if (isMainMethod(asMethod) || isUnitTest(asMethod)) {
                    result.add(asMethod)
                }
            }
        }
    }

    private fun isMainMethod(method: Method): Boolean {
        return method.name == "main" && method.ownerScope.isObjectLike()
    }

    private fun isUnitTest(method: Method): Boolean {
        val testTypes = listOf(
            Types.Test,
            Types.ParameterizedTest
        )
        return method.memberScope.annotations.any {
            val annotationType = it.path.resolve() as ClassType
            annotationType in testTypes
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