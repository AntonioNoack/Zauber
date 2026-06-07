package me.anno.libraries

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

object Libraries {

    private val LOGGER = LogManager.getLogger(Libraries::class)

    const val PROJECT_FILE_EXTENSION = "toml"
    const val PROJECT_FILE_NAME = "Zauber.$PROJECT_FILE_EXTENSION"

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

    fun loadProject(file: File): Library {
        check(file.exists()) { "Project file $file does not exist." }
        check(file.isFile) { "Project file $file must be a file." }
        check(file.canRead()) { "Project file $file cannot be read." }
        check(file.extension == PROJECT_FILE_EXTENSION) {
            "Project file $file has incorrect extension."
        }
        val text = file.readText()
        val toml = TOMLParser.parseTOML(text)
        return loadProject(file.parentFile, toml, "package.", "dependencies.")
    }

    private fun loadProject(file: File, toml: Map<String, Any>, prefix: String, dependencyPrefix: String?): Library {
        val project = Library()
        project.name = toml["${prefix}name"].toString()
        project.version = toml["${prefix}version"].toString()

        val source = toml["${prefix}source"]?.toString() ?: ""
        val sourceFile = if (source.isEmpty()) file else File(file, source)

        project.source = URI("file://${sourceFile.absolutePath}")
        project.newPackage = toml["${dependencyPrefix}newPackage"]?.toString() ?: ""
        project.oldPackage = toml["${dependencyPrefix}oldPackage"]?.toString() ?: ""

        if (dependencyPrefix != null) {
            for ((key, value) in toml.entries.filter { (it) -> it.startsWith(dependencyPrefix) }) {
                val name = key.removePrefix(dependencyPrefix)
                if (value !is String) {
                    LOGGER.warn("Expected dependency '$name' to be a string")
                    continue
                }
                // todo do we support maps and arrays already? would be nice to use them...
                project.dependencies.add(Library().apply {
                    this.name = name
                    this.version = value
                })
            }
        }
        return project
    }
}