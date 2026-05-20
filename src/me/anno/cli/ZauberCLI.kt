package me.anno.cli

import me.anno.cli.impl.*
import java.io.File

object ZauberCLI {

    @JvmStatic
    fun main(args: Array<String>) {
        val command = parse(args)
        command.impl.execute(command.options, command.location)
    }

    val commands = listOf(
        BuildCommand,
        TestCommand,
        RunCommand,
        HelpCommand,
        UsageCommand,
        VersionCommand,
    )

    fun parse(args: Array<String>): Command {
        val options = LinkedHashMap<String, String>()
        var location = File(".")
        if (args.isEmpty()) {
            return Command(commands.first { "usage" in it.names }, options, location)
        }

        val name = args[0]
        val impl = commands.firstOrNull { name in it.names }
            ?: run {
                options["--find"] = name
                return Command(commands.first { "usage" in it.names }, options, location)
            }

        var i = 1
        while (i < args.size) {
            val argI = args[i++]
            val optionName = when {
                argI.startsWith("--") -> argI.substring(2)
                argI.startsWith("-") -> argI.substring(1)
                else -> {
                    location = File(argI)
                    continue
                }
            }

            // todo check if the option is known, and if not, what the most similar one would be

            val value = if (i < args.size && !args[i].startsWith("-")) args[i++] else ""
            options[optionName] = value
        }

        return Command(impl, options, location)
    }
}