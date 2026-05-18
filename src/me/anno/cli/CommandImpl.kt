package me.anno.cli

import java.io.File

abstract class CommandImpl(vararg val names: String) {
    abstract fun execute(options: LinkedHashMap<String, String>, location: File)
    abstract fun printHelp()
}