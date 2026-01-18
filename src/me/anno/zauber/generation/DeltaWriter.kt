package me.anno.zauber.generation

import me.anno.zauber.logging.LogManager
import java.io.File

/**
 * Collects all changes in a folder,
 * and then writes all changed files at once ->
 * saves a lot of disk ops, if few files change
 * */
class DeltaWriter(val root: File) {

    companion object {
        private val LOGGER = LogManager.getLogger(DeltaWriter::class)
    }

    private val oldContent = HashMap<File, String?>()
    private val newContent = HashMap<File, String?>()

    init {
        index(root)
    }

    fun index(file: File) {
        if (file.isDirectory) {
            oldContent[file] = null
            for (child in file.listFiles()!!) {
                index(child)
            }
        } else {
            oldContent[file] = file.readText()
        }
    }

    operator fun get(file: File): String {
        return newContent[file]
            ?: throw IllegalStateException("Missing $file")
    }

    operator fun set(file: File, content: String) {
        newContent[file] = content

        // mark all parents as changed
        var file = file
        while (file != root) {
            file = file.parentFile
            newContent.getOrPut(file) { null }
        }
    }

    fun finish() {
        var numDeleted = 0
        var numChanged = 0
        for (file in oldContent.keys) {
            if (file !in newContent) {
                file.delete()
                numDeleted++
            }
        }
        for ((file, content) in newContent) {
            if (content != null && content != oldContent[file]) {
                file.parentFile.mkdirs()
                file.writeText(content)
                numChanged++
            }
        }
        LOGGER.info("Changed $numChanged and deleted $numDeleted files for ${newContent.size} files in total")
    }
}