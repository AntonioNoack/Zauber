package me.anno.cli.impl

import me.anno.cli.CommandImpl
import java.io.File

object UsageCommand : CommandImpl("usage", "u") {
    override fun execute(options: Options, location: File) {
        options["usage"] = ""
        HelpCommand.execute(options, location)
    }

    override fun printHelp() {
        println("Shows how to use Zauber")
    }
}