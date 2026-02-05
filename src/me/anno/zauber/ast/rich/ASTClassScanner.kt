package me.anno.zauber.ast.rich

import me.anno.zauber.Compile.root
import me.anno.zauber.ast.KeywordSet
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.SuperCallName
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NullableAnyType
import kotlin.math.max

/**
 * to make type-resolution immediately available/resolvable
 * */
abstract class ASTClassScanner(tokens: TokenList) : ZauberASTBuilderBase(tokens, root, true) {

    open fun skipAnnotations() {
        if (tokens.equals(i, "@") && tokens.equals(i + 1, TokenType.NAME)) {
            i += 2
            while (tokens.equals(i, ".") && tokens.equals(i + 1, TokenType.NAME)) {
                i += 2
            }
            skipValueParameters()
        }
    }

    private val listening = ArrayList<Boolean>()
        .apply { add(true) }

    private var depth = 0
    private var hadNamedScope = false

    var nextPackage = root

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

    fun pushNamedScope(name: String, listenType: Int, scopeType: ScopeType): Scope {
        nextPackage = currPackage.getOrPut(name, scopeType)
        val classScope = nextPackage
        classScope.keywords = classScope.keywords or listenType
        classScope.fileName = tokens.fileName
        return classScope
    }

    open fun foundNamedScope(name: String, listenType: KeywordSet, scopeType: ScopeType) {
        val classScope = pushNamedScope(name, listenType, scopeType)

        val genericParams = if (consumeIf("<")) {
            collectGenericParameters(classScope)
        } else emptyList()

        classScope.typeParameters = genericParams
        classScope.hasTypeParameters = true
        if (false) println("Defined type parameters for ${classScope.pathStr}")

        consumeIf("private")
        consumeIf("protected")
        consumeIf("constructor")
        if (tokens.equals(i, TokenType.OPEN_CALL)) {
            // skip constructor params
            i = tokens.findBlockEnd(i, TokenType.OPEN_CALL, TokenType.CLOSE_CALL) + 1
        }

        if (consumeIf(":")) {
            collectSuperNames(classScope)
        }

        handleClassBody(classScope, scopeType)
    }

    fun handleClassBody(classScope: Scope, scopeType: ScopeType) {
        if (scopeType == ScopeType.ENUM_CLASS && tokens.equals(i, "{")) {
            hadNamedScope = true
            handleBlockOpen()
            i++

            collectEnumNames(classScope)
            currPackage.getOrPut("Companion", ScopeType.COMPANION_OBJECT)
        } else {
            hadNamedScope = true
        }
    }

    fun collectGenericParameters(classScope: Scope): List<Parameter> {
        val typeParameters = ArrayList<Parameter>()
        val i0 = i
        var depth = 1
        while (depth > 0 && i < tokens.size) {
            if (depth == 1 && tokens.equals(i, TokenType.NAME) &&
                ((i == i0) || (tokens.equals(i - 1, ",")))
            ) {
                val name = tokens.toString(i)
                val type = NullableAnyType
                typeParameters.add(Parameter(typeParameters.size, name, type, classScope, i))
            }

            if (tokens.equals(i, "<")) depth++
            else if (tokens.equals(i, ">")) depth--
            else when (tokens.getType(i)) {
                TokenType.OPEN_CALL, TokenType.OPEN_ARRAY, TokenType.OPEN_BLOCK -> depth++
                TokenType.CLOSE_CALL, TokenType.CLOSE_ARRAY, TokenType.CLOSE_BLOCK -> depth--
                else -> {}
            }
            i++
        }
        return typeParameters
    }

    fun collectSuperNames(classScope: Scope) {
        while (tokens.equals(i, TokenType.NAME)) {
            val name = tokens.toString(i++)
            // LOGGER.info("discovered $nextPackage extends $name")
            classScope.superCallNames.add(SuperCallName(name, imports))
            if (tokens.equals(i, "<")) {
                i = tokens.findBlockEnd(i, "<", ">") + 1
            }
            if (tokens.equals(i, TokenType.OPEN_CALL)) {
                i = tokens.findBlockEnd(i, TokenType.OPEN_CALL, TokenType.CLOSE_CALL) + 1
            }
            if (tokens.equals(i, TokenType.COMMA)) i++
            else break
        }
    }

    fun collectEnumNames(classScope: Scope) {
        while (i < tokens.size && !(tokens.equals(i, ";") || tokens.equals(i, "}"))) {
            skipAnnotations()

            check(tokens.equals(i, TokenType.NAME)) {
                "Expected name in enum class $currPackage, got ${tokens.err(i)}"
            }
            val name = tokens.toString(i++)

            val childScope = currPackage.getOrPut(name, ScopeType.ENUM_ENTRY_CLASS)
            childScope.hasTypeParameters = true
            if (false) println("Defined type parameters for ${classScope.pathStr}.$name")

            // C# and C/C++ have assignments to specific values
            skipAssignmentUntilComma()

            // skip value parameters
            skipValueParameters()

            // skip block body
            skipBlock()

            if (tokens.equals(i, ",")) i++
        }
    }

    fun skipAssignmentUntilComma() {
        if (tokens.equals(i, "=")) {
            var depth = 0
            while (i < tokens.size) {
                if (depth == 0 && tokens.equals(i, TokenType.COMMA, TokenType.SEMICOLON)) break
                if (tokens.equals(i, TokenType.OPEN_CALL, TokenType.OPEN_BLOCK, TokenType.OPEN_ARRAY)) depth++
                if (tokens.equals(i, TokenType.CLOSE_CALL, TokenType.CLOSE_BLOCK, TokenType.CLOSE_ARRAY)) depth--
                i++
            }
        }
    }

    fun skipValueParameters() {
        if (tokens.equals(i, TokenType.OPEN_CALL)) {
            // skip constructor params
            i = tokens.findBlockEnd(i, TokenType.OPEN_CALL, TokenType.CLOSE_CALL) + 1
        }
    }

    fun skipBlock() {
        if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
            // skip constructor params
            i = tokens.findBlockEnd(i, TokenType.OPEN_BLOCK, TokenType.CLOSE_BLOCK) + 1
        }
    }

    override fun readFileLevel() {
        while (i < tokens.size) {
            val i0 = i
            when (tokens.getType(i)) {
                TokenType.OPEN_BLOCK -> handleBlockOpen()
                TokenType.OPEN_CALL, TokenType.OPEN_ARRAY -> depth++
                TokenType.CLOSE_CALL, TokenType.CLOSE_ARRAY -> depth--
                TokenType.CLOSE_BLOCK -> {
                    @Suppress("Since15")
                    if (listening.removeLast()) {
                        if (listening.isEmpty()) {
                            throw IllegalStateException("Bracket in-balance at ${tokens.err(i)}")
                        }
                        currPackage = currPackage.parent ?: root
                    } else depth--
                }
                else -> if (depth == 0) {
                    collectNamesOnDepth0()
                }
            }
            i = max(i0 + 1, i)
        }
    }

    open fun readPackage() {
        val (path, ni) = tokens.readPath(i)
        currPackage = path
        i = ni
    }

    open fun readImport() {
        val (path, ni) = tokens.readImport(i)
        imports.add(path)
        i = ni
    }

    open fun checkImports(): Boolean {
        when {
            consumeIf("package") -> {
                readPackage()
                return true// without i++
            }
            consumeIf("import") -> {
                readImport()
                return true// without i++
            }
            else -> return false
        }
    }

    open fun collectNamesOnDepth0() {

        if (listening.size == 1) {
            if (checkImports()) return
        }

        if (consumeIf("typealias")) {
            readTypeAlias()
            return
        }

        if (tokens.equals(i, "var") || tokens.equals(i, "val") || tokens.equals(i, "fun")) {
            hadNamedScope = false
            return
        }

        if (listening.last()) {
            checkForTypes()
        }
    }

    open fun checkForTypes() {
        when {
            tokens.equals(i, "class") && tokens.equals(i - 1, "enum") -> {
                check(tokens.equals(++i, TokenType.NAME)) {
                    "Expected name after enum class, got ${tokens.err(i)}"
                }
                val name = tokens.toString(i++)
                foundNamedScope(name, Keywords.NONE, ScopeType.ENUM_CLASS)
            }

            tokens.equals(i, "class") && tokens.equals(i - 1, "inner") -> {
                check(tokens.equals(++i, TokenType.NAME)) {
                    "Expected name after inner class, got ${tokens.err(i)}"
                }
                val name = tokens.toString(i++)
                foundNamedScope(name, Keywords.NONE, ScopeType.INNER_CLASS)
            }

            tokens.equals(i, "class") && !tokens.equals(i - 1, "::") -> {
                check(tokens.equals(++i, TokenType.NAME)) {
                    "Expected name after class, got ${tokens.err(i)}"
                }
                val name = tokens.toString(i++)
                foundNamedScope(name, Keywords.NONE, ScopeType.NORMAL_CLASS)
            }

            tokens.equals(i, "object") && !tokens.equals(i - 1, "companion")
                    && !tokens.equals(i + 1, ":") -> {
                check(tokens.equals(++i, TokenType.NAME)) {
                    "Expected name for object, but got ${tokens.err(i)}"
                }
                val name = tokens.toString(i++)
                foundNamedScope(name, Keywords.NONE, ScopeType.OBJECT)
            }

            consumeIf("companion") -> {
                check(tokens.equals(i++, "object"))
                val name = if (tokens.equals(i, TokenType.NAME)) {
                    tokens.toString(i++)
                } else "Companion"
                foundNamedScope(name, Keywords.NONE, ScopeType.COMPANION_OBJECT)
            }

            consumeIf("interface") -> {
                check(tokens.equals(i, TokenType.NAME)) {
                    "Expected name after interface, got ${tokens.err(i)}"
                }
                val name = tokens.toString(i++)
                val keywords = if (tokens.equals(i - 2, "fun")) Keywords.FUN_INTERFACE else Keywords.NONE
                foundNamedScope(name, keywords, ScopeType.INTERFACE)
            }

            consumeIf("typealias") -> {
                check(tokens.equals(i, TokenType.NAME)) {
                    "Expected name after typealias, got ${tokens.err(i)}"
                }
                val name = tokens.toString(i++)
                foundNamedScope(name, Keywords.NONE, ScopeType.TYPE_ALIAS)
            }
        }
    }

    override fun readSuperCalls(classScope: Scope) {
        throw NotImplementedError()
    }

    override fun readExpression(minPrecedence: Int): Expression {
        throw NotImplementedError()
    }

    override fun readBodyOrExpression(label: String?): Expression {
        throw NotImplementedError()
    }

    override fun readAnnotation(): Annotation {
        throw NotImplementedError()
    }

    override fun readParameterDeclarations(selfType: Type?): List<Parameter> {
        throw NotImplementedError()
    }

    override fun readMethodBody(): ExpressionList {
        throw NotImplementedError()
    }
}