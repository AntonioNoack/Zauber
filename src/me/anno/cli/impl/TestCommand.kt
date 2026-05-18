package me.anno.cli.impl

import me.anno.cli.CommandImpl
import java.io.File

object TestCommand : CommandImpl("test", "t") {
    override fun execute(options: LinkedHashMap<String, String>, location: File) {
        options["test"] = ""
        BuildCommand.execute(options, location)
    }

    override fun printHelp() {
        println("Shortcut for 'build --test'")
    }
}