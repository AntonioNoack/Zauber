package me.anno.cli.impl

import me.anno.cli.CommandImpl
import me.anno.cli.ZauberCLI.commands
import java.io.File

object HelpCommand : CommandImpl("help", "h", "man", "?") {
    override fun execute(options: Options, location: File) {
        if ("usage" in options) {
            return showUsage()
        }
        val commandName = options["command"]
        if (commandName != null) {
            val command = commands.firstOrNull { commandName in it.names }
            if (command != null) {
                showHelp(command)
            } else {
                return showUsage()
            }
        } else {
            for (command in commands) {
                showHelp(command)
            }
        }
    }

    fun showHelp(command: CommandImpl) {
        print("${command.names.first()}: ")
        command.printHelp()
    }

    fun showUsage() {
        println("Usage: Zauber <command> {--optionName optionValue} projectLocation")
        println("  commands: ${commands.map { it.names[0] }}")
        println("  location: project folder for Zauber.build or build file directly, or file to execute")
    }

    override fun printHelp() {
        println("Shows helpful information")
    }
}