package me.anno.support.cpp.preprocessor

import me.anno.utils.IntArrayList
import kotlin.math.max
import kotlin.math.min

class LineNumbers(withNames: Boolean) {

    val lookup = IntArrayList(16)
    val lineNumbers = IntArrayList(16)
    val fileNames = if (withNames) ArrayList<String>() else null

    var numTokens = 0

    /**
     * this must be called for each token
     * */
    fun add(fileName: String, lineNumber: Int) {
        if (lineNumbers.size == 0 ||
            lineNumbers.last() != lineNumber ||
            (fileNames != null && fileNames.last() != fileName)
        ) {
            // println("Next line number: $lineNumber for $numTokens")
            lookup.add(numTokens)
            lineNumbers.add(lineNumber)
            fileNames?.add(fileName)
        }
        numTokens++
    }

    fun findEntryIndex(tokenIndex: Int): Int {
        var idx = lookup.values.binarySearch(tokenIndex, 0, lookup.size)
        if (idx < 0) idx = max(-idx - 2, 0)
        return min(idx, lookup.size - 1)
    }

    fun getLineNumber(entryIndex: Int): Int {
        return lineNumbers[entryIndex]
    }

    fun getFileName(entryIndex: Int): String {
        return fileNames!![entryIndex]
    }
}