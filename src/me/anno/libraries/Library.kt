package me.anno.libraries

import me.anno.support.Language
import me.anno.utils.assertEquals
import me.anno.zauber.logging.LogManager
import java.io.File
import java.net.URI

// todo TOML-style format like in Rust with cargo to add libraries,
//  but we don't want to store anything ourselves,
//  so you just specify links to the GitHub or GitLab repositories,
//  and say which branch to pull,
// todo and we have some global cache folder,
//  where we place these, and at build time, we include their src folders?
//  maybe some project file...
//  or do we want a Zig-style build script?

class Library {

    var name = ""
    var version = ""
    var source: URI? = null

    /*
    * Rust format for reference:
    [package]
    name = "Zauber"
    version = "0.1.0"
    edition = "2024"

    [dependencies]
    lazy_static = "1.5.0"
    gc = { version = "0.5", features = ["derive"] }
    * */

    // todo paths in that library are rewritten in the following way:
    var newPackage = ""
    var oldPackage = ""

    var language = Language.ZAUBER

    val dependencies = ArrayList<Library>()

    override fun toString(): String {
        return "Library(name='$name', version='$version', source=$source, dependencies=$dependencies, " +
                "newPackage='$newPackage', oldPackage='$oldPackage', language=$language)"
    }

    companion object {

        private val LOGGER = LogManager.getLogger(Library::class)

        const val PROJECT_FILE_EXTENSION = "toml"
        const val PROJECT_FILE_NAME = "Zauber.$PROJECT_FILE_EXTENSION"

        fun loadProject(file: File): Library {
            val toml = loadTOML(file)
            return loadProject(file.parentFile, toml)
        }

        fun loadTOML(file: File): Map<String, Any> {
            val file = file.absoluteFile
            check(file.exists()) { "Project file $file does not exist." }
            check(file.isFile) { "Project file $file must be a file." }
            check(file.canRead()) { "Project file $file cannot be read." }
            check(file.extension == PROJECT_FILE_EXTENSION) {
                "Project file $file has incorrect extension."
            }
            val text = file.readText()
            return TOMLParser.parseTOML(text)
        }

        private fun loadProject(folder: File,  toml: Map<String, Any>): Library {
            // println("Loading project @$folder, $toml")

            val project = Library()
            project.name = toml["package.name"].toString()
            project.version = toml["package.version"].toString()

            val source = toml["package.source"]?.toString() ?: ""
            val sourceFile = if (source.isEmpty()) folder else File(folder, source)
            val sourceURL = URI("file://${sourceFile.absolutePath}")

            project.source = sourceURL
            project.newPackage = toml["package.newPackage"]?.toString() ?: ""
            project.oldPackage = toml["package.oldPackage"]?.toString() ?: ""

            val dependencyPrefix = "dependencies."
            for ((key, value) in toml.entries.filter { (it) -> it.startsWith(dependencyPrefix) }) {
                val name = key.removePrefix(dependencyPrefix)
                when (value) {
                    is Map<*, *> -> {
                        if ("source" !in value) {
                            LOGGER.warn("Expected dependency '$name' to have source parameter")
                            continue
                        }

                        val version = value["version"]?.toString()
                        var source = value["source"].toString()
                        if (version != null) source += "/$version"
                        if ("://" !in source) {
                            val joinedFile = File(folder, source)
                            source = "file://${joinedFile.absolutePath}"
                        }

                        // println("Loading library '$name' from $source")
                        project.dependencies.add(loadLibrary(name, URI(source), version))
                    }
                    else -> {
                        LOGGER.warn("Expected dependency '$name' to be an object")
                    }
                }
            }
            return project
        }

        // todo load libraries in parallel (?)
        fun loadLibrary(nameOverride: String, source: URI, expectedVersion: String?): Library {
            if (source.scheme == "file") {
                val folder = File(source.path)
                val configFile = File(folder, PROJECT_FILE_NAME)
                val toml = loadTOML(configFile)
                val library = loadProject(folder, toml)
                library.name = nameOverride
                if (expectedVersion != null) {
                    assertEquals(expectedVersion, library.version) {
                        "Version mismatch for $source"
                    }
                }
                return library
            } else {
                TODO("Download/cache $source on disk somewhere")
            }
        }

        fun extractFileFromURI(uri: URI?): File {
            check(uri != null) { "Missing project source file" }
            when (uri.scheme) {
                "file" -> return File(uri.path)
                "jar" -> TODO("'Extract' jar")
                else -> TODO("Unknown scheme ${uri.scheme}")
            }
        }
    }
}