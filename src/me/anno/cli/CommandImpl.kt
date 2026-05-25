package me.anno.cli

import me.anno.cli.impl.Options
import java.io.File

abstract class CommandImpl(vararg val names: String) {
    abstract fun execute(options: Options, location: File)
    abstract fun printHelp()
}