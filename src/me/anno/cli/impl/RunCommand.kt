package me.anno.cli.impl

import me.anno.cli.CommandImpl
import java.io.File

object RunCommand : CommandImpl("run", "r") {
    override fun execute(options: LinkedHashMap<String, String>, location: File) {
        options["run"] = ""
        BuildCommand.execute(options, location)
    }

    override fun printHelp() {
        println("Shortcut for 'build --run'")
    }
}