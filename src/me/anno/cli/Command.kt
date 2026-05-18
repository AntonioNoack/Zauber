package me.anno.cli

import java.io.File

class Command(val impl: CommandImpl, val options: LinkedHashMap<String, String>, val location: File)