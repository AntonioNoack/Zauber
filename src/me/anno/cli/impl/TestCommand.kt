package me.anno.cli.impl

import me.anno.cli.CommandImpl
import java.io.File

object TestCommand : CommandImpl("test", "t") {
    override fun execute(options: Options, location: File) {
        options["test"] = ""
        options["only-test"] = ""
        BuildCommand.execute(options, location)
    }

    override fun printHelp() {
        println("Shortcut for 'build --test'")
    }
}