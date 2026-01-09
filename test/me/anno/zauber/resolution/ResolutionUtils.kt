package me.anno.zauber.resolution

import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.ASTClassScanner.collectNamedClasses
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.ZauberASTBuilder
import me.anno.zauber.expansion.DefaultParameterExpansion.createDefaultParameterFunctions
import me.anno.zauber.tokenizer.ZauberTokenizer
import me.anno.zauber.typeresolution.TypeResolution.resolveTypesAndNames
import me.anno.zauber.typeresolution.TypeResolutionTest.Companion.ctr
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType

object ResolutionUtils {
    fun typeResolveScope(code: String): Scope {

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
            ZauberTokenizer(content, "?").tokenize()
        }

        for (index in tokens.indices) {
            collectNamedClasses(tokens[index])
        }

        for (index in tokens.indices) {
            ZauberASTBuilder(tokens[index], root).readFileLevel()
        }

        val packageNames = sources.mapIndexed { index, content ->
            if (index == 0) testScopeName else
                content
                    .substringAfter("package ") // remove package prefix
                    .substringBefore('\n') // split first line
                    .substringBefore(';') // remove ; if there is any
        }

        createDefaultParameterFunctions(root)

        for (packageName in packageNames) {
            val scope = root.children.firstOrNull { it.name == packageName }
                ?: throw IllegalStateException("Missing '$packageName' in root, available: ${root.children.map { it.name }}")
            resolveTypesAndNames(scope)
        }

        return root.children.first { it.name == testScopeName }
    }

    operator fun Scope.get(name: String): Scope {
        return children.firstOrNull { it.name == name }
            ?: throw IllegalStateException("Tried finding '$name', but only found ${children.map { it.name }}")
    }

    fun Scope.getField(name: String): Field {
        return fields.firstOrNull { it.name == name && it.byParameter == null }
            ?: throw IllegalStateException("Tried finding '$name', but only found ${fields.map { it.name }}")
    }

    fun Scope.firstChild(scopeType: ScopeType): Scope {
        return children.firstOrNull { it.scopeType == scopeType }
            ?: throw IllegalStateException("Tried finding '$scopeType', but only found ${children.map { it.scopeType }}")
    }

}