package me.anno.zauber.langserver.logging

import java.io.File
import java.io.FileOutputStream

object AppendLogging {
    val file = File("/home/antonio/Desktop/zls.log")

    fun append(message: String) {
        if (!file.exists()) file.createNewFile()
        FileOutputStream(file, true).bufferedWriter().use { writer ->
            writer.write(message)
            writer.write("\n")
        }
    }

    fun info(message: String) {
        val time = System.currentTimeMillis()
        append("[INFO,$time] $message")
    }
}