package me.anno.zauber.resolution

import me.anno.utils.ResetThreadLocal
import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.ZauberASTClassScanner.Companion.scanClasses
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.tokenizer.ZauberTokenizer
import me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.ctr

object ResolutionUtils {

    fun typeResolveScope(code: String, reset: Boolean = true): Scope {

        if (reset) ResetThreadLocal.reset()

        val testScopeName = "test${ctr++}"

        val sources = code
            .split("\npackage ")
            .mapIndexed { index, content ->
                if (index == 0) {
                    testScopeName to "package $testScopeName\n\n$content"
                } else {
                    var linebreak = content.indexOf('\n')
                    if (linebreak < 0) linebreak = content.length
                    val packageName = content.substring(0, linebreak).trim()
                    packageName to "package $content"
                }
            }

        for (i in sources.indices) {
            val (packageName, content) = sources[i]
            if (false) {
                println("Test.zbr")
                println(content.formatLines())
            }
            val scope = getScope(packageName)
            // sit, so we can add the parts no matter what...
            // may cause us to skip some initialization :/
            scope.addInitPart(scope.scopeInitType) {
                val tokenizer = ZauberTokenizer(content, "Test.zbr")
                scanClasses(tokenizer.tokenize())
            }
        }

        return root.children.first { it.name == testScopeName }
    }

    fun getScope(path: String): Scope {
        val parts = path.split('.')
        var scope = root
        for (part in parts) {
            check(part.trim() == part)
            scope = scope.getOrPut(part, ScopeType.PACKAGE)
        }
        return scope
    }

    fun String.formatLines(): String {
        val lines = lines()
        val pad = lines.size.toString().length
        return lines.mapIndexed { lineIndex, line ->
            "${(lineIndex + 1).toString().padStart(pad)} | $line"
        }.joinToString("\n")
    }

    operator fun Scope.get(name: String): Scope {
        return this[ScopeInitType.AFTER_DISCOVERY].children.firstOrNull { it.name == name }
            ?: throw IllegalStateException("Tried finding '$name', but only found ${children.map { it.name }}")
    }

    fun Scope.getField(name: String): Field {
        return this[ScopeInitType.AFTER_DISCOVERY].fields.firstOrNull { it.name == name && it.byParameter == null }
            ?: throw IllegalStateException("Tried finding '$name', but only found ${fields.map { it.name }}")
    }

    fun Scope.firstChild(scopeType: ScopeType): Scope {
        return this[ScopeInitType.AFTER_DISCOVERY].children.firstOrNull { it.scopeType == scopeType }
            ?: throw IllegalStateException("Tried finding '$scopeType', but only found ${children.map { it.scopeType }}")
    }

}