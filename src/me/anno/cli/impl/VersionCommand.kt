package me.anno.cli.impl

import me.anno.Zauber.version
import me.anno.cli.CommandImpl
import java.io.File

object VersionCommand : CommandImpl("version", "v", "about") {
    override fun execute(options: LinkedHashMap<String, String>, location: File) {
        println("Zauber Version: $version")
        println("Zauber is developed by Antonio Noack from Jena, Germany")
    }

    override fun printHelp() {
        println("Shows the version ($version)")
    }
}