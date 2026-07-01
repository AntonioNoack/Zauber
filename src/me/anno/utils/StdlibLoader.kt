package me.anno.utils

import me.anno.generation.InheritanceTable
import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.ast.rich.parser.ZauberASTClassScanner
import me.anno.zauber.tokenizer.ZauberTokenizer

object StdlibLoader {
    fun loadCode(fileName: String) {
        val src = InheritanceTable::class.java
            .classLoader.getResourceAsStream(fileName)!!
            .readBytes().decodeToString()
        val tokens = ZauberTokenizer(src, fileName).tokenize()
        ZauberASTClassScanner.scanClasses(tokens)
    }

    fun loadLazyCode(fileName: String): ResetThreadLocal<Unit> {
        return threadLocal { loadCode(fileName) }
    }
}