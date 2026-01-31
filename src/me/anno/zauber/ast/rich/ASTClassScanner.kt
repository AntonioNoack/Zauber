package me.anno.zauber.ast.rich

import me.anno.zauber.Compile.root
import me.anno.zauber.ast.KeywordSet
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ParameterList.Companion.resolveGenerics
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.SuperCallName
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NullableAnyType
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.UnresolvedType

/**
 * to make type-resolution immediately available/resolvable
 * */
class ASTClassScanner(tokens: TokenList) : ZauberASTBuilderBase(tokens, root, true) {

    companion object {
        fun collectNamedClassesForTypeResolution(allTokens: List<TokenList>) {
            for (i in allTokens.indices) {
                val tokens = allTokens[i]
                collectNamedClasses(tokens)
            }
        }

        fun collectNamedClasses(tokens: TokenList) {
            ASTClassScanner(tokens).collectNamedClassesImpl()
        }

        // todo I don't really want to deal with them during type-resolution,
        //  because they are more of an import and should be able to be resolved really quickly
        //  -> index type aliases when collecting names already
        //  -> resolve the types when reading types already
        fun resolveTypeAliases(type: Type): Type {
            var currType = type
            while (true) {
                if (currType is UnresolvedType) {
                    currType = currType.resolve()
                    continue
                }
                if (currType is ClassType && currType.clazz.isTypeAlias()) {
                    val scope = currType.clazz

                    val genericNames = scope.typeParameters
                    if (genericNames.isEmpty() || currType.typeParameters == null) {
                        currType = scope.selfAsTypeAlias!!
                        continue
                    }

                    val genericValues = ParameterList(genericNames, currType.typeParameters)
                    currType = genericValues.resolveGenerics(null, currType)
                    continue
                }
                // todo if typeAlias in any of the typeParameters, replace it, too
                if (currType is ClassType && currType.typeParameters != null) {
                    return ClassType(currType.clazz, currType.typeParameters.map { resolveTypeAliases(it) })
                }
                return currType
            }
        }
    }

    private val listening = ArrayList<Boolean>()
        .apply { add(true) }

    private var depth = 0
    private var hadNamedScope = false

    private var nextPackage = root

    private fun handleBlockOpen() {
        if (hadNamedScope) {
            listening.add(true)
            currPackage = nextPackage
        } else {
            depth++
            listening.add(false)
        }
        hadNamedScope = false
    }

    private fun foundNamedScope(name: String, listenType: KeywordSet, scopeType: ScopeType?) {
        nextPackage = currPackage.getOrPut(name, scopeType)
        val classScope = nextPackage
        classScope.keywords = classScope.keywords or listenType
        classScope.fileName = tokens.fileName

        // LOGGER.info("discovered $nextPackage")

        var j = i
        val genericParams = if (tokens.equals(j, "<")) {
            val typeParameters = ArrayList<Parameter>()

            j++
            var depth = 1
            while (depth > 0) {
                if (depth == 1 && tokens.equals(j, TokenType.NAME) &&
                    ((j == i + 1) || (tokens.equals(j - 1, ",")))
                ) {
                    val name = tokens.toString(j)
                    val type = NullableAnyType
                    typeParameters.add(Parameter(typeParameters.size, name, type, classScope, j))
                }

                if (tokens.equals(j, "<")) depth++
                else if (tokens.equals(j, ">")) depth--
                else when (tokens.getType(j)) {
                    TokenType.OPEN_CALL, TokenType.OPEN_ARRAY, TokenType.OPEN_BLOCK -> depth++
                    TokenType.CLOSE_CALL, TokenType.CLOSE_ARRAY, TokenType.CLOSE_BLOCK -> depth--
                    else -> {}
                }
                j++
            }

            typeParameters
        } else emptyList()

        classScope.typeParameters = genericParams
        classScope.hasTypeParameters = true
        if (false) println("Defined type parameters for ${classScope.pathStr}")

        if (tokens.equals(j, "private")) j++
        if (tokens.equals(j, "protected")) j++
        if (tokens.equals(j, "constructor")) j++
        if (tokens.equals(j, TokenType.OPEN_CALL)) {
            // skip constructor params
            j = tokens.findBlockEnd(j, TokenType.OPEN_CALL, TokenType.CLOSE_CALL) + 1
        }
        if (tokens.equals(j, ":")) {
            j++
            while (tokens.equals(j, TokenType.NAME)) {
                val name = tokens.toString(j++)
                // LOGGER.info("discovered $nextPackage extends $name")
                classScope.superCallNames.add(SuperCallName(name, imports))
                if (tokens.equals(j, "<")) {
                    j = tokens.findBlockEnd(j, "<", ">") + 1
                }
                if (tokens.equals(j, TokenType.OPEN_CALL)) {
                    j = tokens.findBlockEnd(j, TokenType.OPEN_CALL, TokenType.CLOSE_CALL) + 1
                }
                if (tokens.equals(j, TokenType.COMMA)) j++
                else break
            }
        }

        if (scopeType == ScopeType.ENUM_CLASS && tokens.equals(j, "{")) {
            hadNamedScope = true
            handleBlockOpen()
            j++

            while (j < tokens.size && !(tokens.equals(j, ";") || tokens.equals(j, "}"))) {
                check(tokens.equals(j, TokenType.NAME)) {
                    "Expected name in enum class $currPackage, got ${tokens.err(j)}"
                }
                val name = tokens.toString(j++)

                val childScope = currPackage.getOrPut(name, ScopeType.ENUM_ENTRY_CLASS)
                childScope.hasTypeParameters = true
                if (false) println("Defined type parameters for ${classScope.pathStr}.$name")

                // skip value parameters
                if (tokens.equals(j, "(")) {
                    j = tokens.findBlockEnd(j, "(", ")") + 1
                }

                // skip block body
                if (tokens.equals(j, "{")) {
                    j = tokens.findBlockEnd(j, "{", "}") + 1
                }

                if (tokens.equals(j, ",")) j++
            }

            currPackage.getOrPut("Companion", ScopeType.COMPANION_OBJECT)

            i = j // skip stuff

        } else {
            hadNamedScope = true
            i = j // skip stuff
        }

        // println("Found class '$name', tp: $genericParams, scope: $classScope, scopeType: ${classScope.scopeType}")
    }

    private fun collectNamedClassesImpl() {
        while (i < tokens.size) {
            when (tokens.getType(i)) {
                TokenType.OPEN_BLOCK -> handleBlockOpen()
                TokenType.OPEN_CALL, TokenType.OPEN_ARRAY -> depth++
                TokenType.CLOSE_CALL, TokenType.CLOSE_ARRAY -> depth--
                TokenType.CLOSE_BLOCK -> {
                    @Suppress("Since15")
                    if (listening.removeLast()) {
                        currPackage = currPackage.parent ?: root
                    } else depth--
                }
                else -> if (depth == 0 && collectNamesOnDepth0()) {
                    // skip i++
                    continue
                }
            }
            i++
        }
    }

    private fun collectNamesOnDepth0(): Boolean {

        if (listening.size == 1) {
            when {
                consumeIf("package") -> {
                    val (path, ni) = tokens.readPath(i)
                    currPackage = path
                    i = ni
                    return true// without i++
                }
                consumeIf("import") -> {
                    val (path, ni) = tokens.readImport(i)
                    imports.add(path)
                    i = ni
                    return true// without i++
                }
            }
        }

        if (consumeIf("typealias")) {
            readTypeAlias()
            return true// without i++
        }

        if (tokens.equals(i, "var") || tokens.equals(i, "val") || tokens.equals(i, "fun")) {
            hadNamedScope = false
            return false
        }

        if (listening.last()) {
            when {
                tokens.equals(i, "class") && tokens.equals(i - 1, "enum") -> {
                    check(tokens.equals(++i, TokenType.NAME))
                    val name = tokens.toString(i++)
                    foundNamedScope(name, Keywords.NONE, ScopeType.ENUM_CLASS)
                    return true// without i++
                }

                tokens.equals(i, "class") && tokens.equals(i - 1, "inner") -> {
                    check(tokens.equals(++i, TokenType.NAME))
                    val name = tokens.toString(i++)
                    foundNamedScope(name, Keywords.NONE, ScopeType.INNER_CLASS)
                    return true// without i++
                }

                tokens.equals(i, "class") && !tokens.equals(i - 1, "::") -> {
                    check(tokens.equals(++i, TokenType.NAME)) {
                        "Expected name after class, got ${tokens.err(i)}"
                    }
                    val name = tokens.toString(i++)
                    foundNamedScope(name, Keywords.NONE, ScopeType.NORMAL_CLASS)
                    return true// without i++
                }

                tokens.equals(i, "object") && !tokens.equals(i - 1, "companion")
                        && !tokens.equals(i + 1, ":") -> {
                    check(tokens.equals(++i, TokenType.NAME)) {
                        "Expected name for object, but got ${tokens.err(i)}"
                    }
                    val name = tokens.toString(i++)
                    foundNamedScope(name, Keywords.NONE, ScopeType.OBJECT)
                    return true// without i++
                }

                consumeIf("companion") -> {
                    check(tokens.equals(i++, "object"))
                    val name = if (tokens.equals(i, TokenType.NAME)) {
                        tokens.toString(i++)
                    } else "Companion"
                    foundNamedScope(name, Keywords.NONE, ScopeType.COMPANION_OBJECT)
                    return true// without i++
                }

                consumeIf("interface") -> {
                    check(tokens.equals(i, TokenType.NAME))
                    val name = tokens.toString(i++)
                    val keywords = if (tokens.equals(i - 2, "fun")) Keywords.FUN_INTERFACE else Keywords.NONE
                    foundNamedScope(name, keywords, ScopeType.INTERFACE)
                    return true// without i++
                }

                consumeIf("typealias") -> {
                    check(tokens.equals(i, TokenType.NAME))
                    val name = tokens.toString(i++)
                    foundNamedScope(name, Keywords.NONE, ScopeType.TYPE_ALIAS)
                    return true// without i++
                }
            }
        }

        return false
    }

    override fun readExpression(minPrecedence: Int): Expression {
        throw NotImplementedError()
    }

    override fun readBodyOrExpression(label: String?): Expression {
        throw NotImplementedError()
    }

    override fun readParameterDeclarations(selfType: Type?): List<Parameter> {
        throw NotImplementedError()
    }

    override fun readMethodBody(): ExpressionList {
        throw NotImplementedError()
    }
}