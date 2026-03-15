package me.anno.zauber.resolution

import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.ZauberASTClassScanner.Companion.scanClasses
import me.anno.zauber.expansion.DefaultParameterExpansion.createDefaultParameterFunctions
import me.anno.zauber.expansion.OverriddenMethods.resolveOverrides
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.tokenizer.ZauberTokenizer
import me.anno.zauber.typeresolution.TypeResolution.resolveTypesAndNames
import me.anno.zauber.types.Types
import me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.ctr

object ResolutionUtils {
    fun typeResolveScope(code: String): Scope {

        root.clear()
        Types.register()

        val testScopeName = "test${ctr++}"

        val sources = code
            .split("\npackage ")
            .mapIndexed { index, content ->
                if (index == 0) {
                    """
                        package $testScopeName
                        
                        $content
                    """.trimIndent()
                } else {
                    "package $content"
                }
            }

        val tokens = sources.map { content ->
            if (false) {
                println("Test.zbr")
                println(content.formatLines())
            }
            ZauberTokenizer(content, "Test.zbr").tokenize()
        }

        for (index in tokens.indices) {
            scanClasses(tokens[index])
        }

        createDefaultParameterFunctions(root)
        resolveOverrides(root)

        return root.children.first { it.name == testScopeName }.scope
    }

    fun String.formatLines(): String {
        val lines = lines()
        val pad = lines.size.toString().length
        return lines.mapIndexed { lineIndex, line ->
            "${(lineIndex + 1).toString().padStart(pad)} | $line"
        }.joinToString("\n")
    }

    operator fun Scope.get(name: String): Scope {
        val child = scope.children.firstOrNull { it.name == name }
            ?: throw IllegalStateException("Tried finding '$name', but only found ${children.map { it.name }}")
        return child.scope
    }

    fun Scope.getField(name: String): Field {
        return scope.fields.firstOrNull { it.name == name && it.byParameter == null }
            ?: throw IllegalStateException("Tried finding '$name', but only found ${fields.map { it.name }}")
    }

    fun Scope.firstChild(scopeType: ScopeType): Scope {
        val child = scope.children.firstOrNull { it.scopeType == scopeType }
            ?: throw IllegalStateException("Tried finding '$scopeType', but only found ${children.map { it.scopeType }}")
        return child.scope
    }

}