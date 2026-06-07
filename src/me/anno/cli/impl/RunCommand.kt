package me.anno.cli.impl

import me.anno.cli.CommandImpl
import java.io.File

object RunCommand : CommandImpl("run", "r") {

    override fun execute(options: Options, location: File) {
        options["run"] = ""
        if ("target" !in options) options["target"] = "runtime"
        BuildCommand.execute(options, location)
    }

    override fun printHelp() {
        println("Shortcut for 'build --run'")
    }
}