package me.anno.cli.impl

import me.anno.cli.CommandImpl
import me.anno.libraries.Libraries
import java.io.File

object BuildCommand : CommandImpl("build", "b") {
    override fun execute(options: LinkedHashMap<String, String>, location: File) {
        readFiles(location)
    }

    override fun printHelp() {
        println("Builds the project or file")
        println("  Options:")
        println("    --run: also run the project")
        println("    --test: also run unit tests; you can specify a specific test, too")
        println("    --target: define which target to compile to, options: [javascript, java, wasm, rust, c++]")
    }

    fun readFiles(location: File) {
        if (location.isDirectory) {
            // try to find project file & load it
            val projectFile = File(location, Libraries.PROJECT_FILE_NAME)
            readFiles(projectFile)
        } else when (location.extension.lowercase()) {
            "zauber", "zbr" -> {
                // todo read using Zauber
                TODO("compile single zauber file, and maybe what it depends on...")
            }
            Libraries.PROJECT_FILE_EXTENSION -> {
                val project = Libraries.loadProject(location)
                TODO("Compile project $project")
            }
        }
    }
}