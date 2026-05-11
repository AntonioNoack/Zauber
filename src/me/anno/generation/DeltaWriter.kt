package me.anno.generation

import me.anno.zauber.logging.LogManager
import java.io.File

/**
 * Collects all changes in a folder,
 * and then writes all changed files at once ->
 * saves a lot of disk ops, if few files change
 * */
abstract class DeltaWriter<V>(val root: File) {

    companion object {
        private val LOGGER = LogManager.getLogger(DeltaWriter::class)
    }

    private val oldContent = HashMap<File, String?>()
    val newContent = HashMap<File, V?>()

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

    operator fun get(file: File): V? {
        return newContent[file]
    }

    operator fun set(file: File, content: V) {
        newContent[file] = content

        // mark all parents as changed
        var file = file
        while (file != root) {
            file = file.parentFile
            newContent.getOrPut(file) { null }
        }
    }

    abstract fun finishContent(file: File, content: V): String

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
            if (content != null) {
                val strContent = finishContent(file, content)
                if (strContent != oldContent[file]) {
                    file.parentFile.mkdirs()
                    file.writeText(strContent)
                    numChanged++
                }
            }
        }
        LOGGER.info("Changed $numChanged and deleted $numDeleted files for ${newContent.size} files in total")
    }
}