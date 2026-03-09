package me.anno.zauber.ast.rich

import me.anno.langserver.VSCodeModifier
import me.anno.langserver.VSCodeType
import me.anno.zauber.Compile.root
import me.anno.zauber.ast.KeywordSet
import me.anno.zauber.ast.rich.FieldGetterSetter.createBackingField
import me.anno.zauber.ast.rich.FieldGetterSetter.createGetterMethod
import me.anno.zauber.ast.rich.FieldGetterSetter.createSetterMethod
import me.anno.zauber.ast.rich.FieldGetterSetter.createValueField
import me.anno.zauber.ast.rich.FieldGetterSetter.finishField
import me.anno.zauber.ast.rich.FieldGetterSetter.needsGetter
import me.anno.zauber.ast.rich.WhereConditions.readWhereConditions
import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.scope.lazy.LazyExpression
import me.anno.zauber.scope.lazy.TokenSubList
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.types.SuperCallName
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NullableAnyType
import me.anno.zauber.types.Types.UnitType
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
        classScope.keywords = classScope.keywords or popKeywords()

        val genericParams = if (consumeIf("<")) {
            collectGenericParameters(classScope)
        } else emptyList()

        classScope.typeParameters = genericParams
        classScope.hasTypeParameters = true
        if (false) println("Defined type parameters for ${classScope.pathStr}")

        if (consumeIf("private")) keywords = keywords or Keywords.PRIVATE
        if (consumeIf("protected")) keywords = keywords or Keywords.PROTECTED

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
        when {
            listening.size == 1 && checkImports() -> return
            consumeIf("typealias") -> readTypeAlias()
            consumeIf("var") || consumeIf("val") -> readField()
            consumeIf("fun") -> readMethod()
            consumeIf("constructor") -> readConstructor()
            consumeIf("external") -> keywords = keywords or Keywords.EXTERNAL
            consumeIf("public") -> keywords = keywords or Keywords.PUBLIC
            consumeIf("protected") -> keywords = keywords or Keywords.PROTECTED
            consumeIf("private") -> keywords = keywords or Keywords.PRIVATE
            consumeIf("abstract") -> keywords = keywords or Keywords.ABSTRACT
            listening.last() -> checkForTypes()
        }
    }

    open fun readField() {
        hadNamedScope = false
        val origin = origin(i - 1)
        val isMutable = tokens.equals(i - 1, "var")
        val end = findFieldNameEnd()
        val name = tokens.toString(end - 1)

        val ownerScope = currPackage
        val fieldScope = ownerScope.generate(name, origin, ScopeType.FIELD)
        pushScope(fieldScope) {

            val genericParams = if (consumeIf("<")) {
                collectGenericParameters(fieldScope)
            } else emptyList()

            check(genericParams.isEmpty()) { "TypeParams for field not yet supported" }

            val nameStart = i

            check(tokens.equals(end - 1, TokenType.NAME, TokenType.KEYWORD)) {
                "Expected field name at ${tokens.err(end - 1)}"
            }

            check(end > nameStart) { "Expected field name at ${tokens.err(nameStart)}" }

            val selfType = if (nameStart < end - 1) readSelfType(end) else null
            val name = consumeName(VSCodeType.PROPERTY, VSCodeModifier.DECLARATION.flag)

            var valueType = if (consumeIf(":")) {
                readType(selfType, true)
            } else null

            val initialValue = if (consumeIf("=")) {
                readLazyValue()
            } else null

            val getterVisibility = readVisibility()
            var setterVisibility = getterVisibility
            var getterOrigin = origin
            val getterBody: Expression? = if (consumeIf("get")) {
                getterOrigin = origin(i - 1)
                val body = if (consumeIf(TokenType.OPEN_CALL)) {
                    consume(TokenType.CLOSE_CALL)
                    if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
                        readLazyBody()
                    } else if (consumeIf("=")) {
                        val originI = origin(i - 1)
                        ReturnExpression(readLazyValue(), null, fieldScope, originI)
                    } else throw IllegalStateException("Expected body for getter, got neither = nor { at ${tokens.err(i)}")
                } else null
                setterVisibility = readVisibility()
                body
            } else null

            lateinit var setterName: String
            var setterOrigin = origin
            val setterBody: Expression? = if (consumeIf("set")) {
                setterOrigin = origin(i - 1)
                if (consumeIf(TokenType.OPEN_CALL)) {
                    setterName = consumeName(VSCodeType.PARAMETER, VSCodeModifier.DECLARATION.flag)
                    val setterType = if (consumeIf(":")) {
                        readType(null, true)
                    } else null
                    if (valueType == null) valueType = setterType

                    consume(TokenType.CLOSE_CALL)
                    if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
                        readLazyBody()
                    } else if (consumeIf("=")) {
                        val originI = origin(i - 1)
                        ReturnExpression(readLazyValue(), null, fieldScope, originI)
                    } else throw IllegalStateException("Expected body for getter, got neither = nor { at ${tokens.err(i)}")
                } else null
            } else null

            val field = ownerScope.addField(
                selfType, selfType != null, isMutable, null,
                name, valueType, initialValue, popKeywords(), origin
            )
            fieldScope.selfAsField = field

            if (getterBody != null) {
                val backingField = createBackingField(field, getterBody.scope, getterOrigin)
                createGetterMethod(field, getterBody, backingField, getterBody.scope, getterOrigin)
            }

            if (setterBody != null) {
                val backingField = createBackingField(field, setterBody.scope, setterOrigin)
                val valueField = createValueField(field, setterName, setterBody.scope, setterOrigin)
                createSetterMethod(field, setterBody, backingField, valueField, setterBody.scope, setterOrigin)
            }

            if (needsGetter(field)) {
                finishField(field)
            }

            val getter = field.getter
            val setter = field.setter
            if (getter != null) getter.keywords = getter.keywords or getterVisibility
            if (setter != null) setter.keywords = setter.keywords or setterVisibility
        }
    }

    private fun popKeywords(): Int {
        val k = keywords
        keywords = 0
        return k
    }

    private fun readVisibility(): Int {
        var flags = 0
        while (i < tokens.size) {
            when {
                consumeIf("public") -> {}
                consumeIf("private") -> flags = flags or Keywords.PRIVATE
                consumeIf("protected") -> flags = flags or Keywords.PROTECTED
                else -> return flags
            }
        }
        return flags
    }

    private fun findFieldNameEnd(): Int {
        // val x, var A<>.x
        // val x: Int, val x = 0
        //  end symbols: [:, =, get(), set(), public, private, protected]
        var end = i
        var depth = 0
        findFieldEnd@ while (end < tokens.size) {
            val j0 = end++
            when (tokens.getType(j0)) {
                TokenType.OPEN_CALL, TokenType.OPEN_ARRAY, TokenType.OPEN_BLOCK -> depth++
                TokenType.CLOSE_CALL, TokenType.CLOSE_ARRAY, TokenType.CLOSE_BLOCK -> depth--
                else -> when {
                    tokens.equals(j0, "<") -> depth++
                    tokens.equals(j0, ">") -> depth--
                    tokens.equals(j0, ":", "=", "get", "set", "public", "private", "protected") -> {
                        if (depth == 0) return j0
                    }
                }
            }
            check(depth >= 0) { "Invalid depth @${tokens.err(i)}" }
        }

        throw IllegalStateException("Missing field end at ${tokens.err(i)}")
    }

    open fun readConstructor() {
        val origin = origin(i - 1)
        val classScope = currPackage
        val constrScope = classScope.generate("constructor", origin, ScopeType.CONSTRUCTOR)
        hadNamedScope = false

        pushScope(constrScope) {
            val selfType = classScope.typeWithArgs
            val parameters = readParameterDeclarations(selfType)

            val superCall = if (consumeIf(":")) {
                TODO("read inner super call")
            } else null

            val body = if (tokens.equals(i, TokenType.OPEN_BLOCK)) readLazyBody() else null
            constrScope.selfAsConstructor = Constructor(
                parameters,
                constrScope, superCall, body,
                popKeywords(), origin
            )
        }
    }

    open fun readMethod() {
        val origin = origin(i - 1)
        hadNamedScope = false

        val end = findParameterStart()
        val name = tokens.toString(end - 1)
        val methodScope = currPackage.generate(name, origin, ScopeType.METHOD)

        pushScope(methodScope) {
            // todo skip these for now, and read them when we know the name...
            val genericParams = if (consumeIf("<")) {
                collectGenericParameters(methodScope)
            } else emptyList()

            val nameStart = i

            check(tokens.equals(end - 1, TokenType.NAME, TokenType.KEYWORD)) {
                "Expected field name at ${tokens.err(end - 1)}"
            }

            check(end > nameStart) { "Expected field name at ${tokens.err(nameStart)}" }

            val selfType = if (nameStart < end - 1) readSelfType(end) else null

            val name = consumeName(VSCodeType.METHOD, VSCodeModifier.DECLARATION.flag)
            val parameters = readParameterDeclarations(selfType)
            val whereConditions = readWhereConditions()

            val returnType = if (consumeIf(":")) {
                readType(selfType, true)
            } else if (tokens.equals(i, "{")) {
                UnitType
            } else null

            val body = when {
                tokens.equals(i, TokenType.OPEN_BLOCK) -> readLazyBody()
                consumeIf("=") -> {
                    val originI = origin(i - 1)
                    ReturnExpression(readLazyValue(), null, methodScope, originI)
                }
                else -> null
            }

            methodScope.selfAsMethod = Method(
                selfType, selfType != null, name,
                genericParams, parameters,
                methodScope, returnType, whereConditions, body,
                popKeywords(), origin
            )
        }
    }

    private fun readLazyBody(): Expression {
        return pushBlock(ScopeType.METHOD_BODY, "body") { scope ->
            val tokens1 = TokenSubList(tokens, i, tokens.size, imports)
            val expr = LazyExpression(tokens1, scope, origin(i))
            i = tokens.size
            expr
        }
    }

    private fun findLazyValueEnd(): Int {
        var end = i
        var depth = 0
        searchEnd@ while (end < tokens.size) {
            val j0 = end++
            when (tokens.getType(j0)) {
                TokenType.OPEN_CALL, TokenType.OPEN_ARRAY, TokenType.OPEN_BLOCK -> depth++
                TokenType.CLOSE_CALL, TokenType.CLOSE_ARRAY, TokenType.CLOSE_BLOCK -> depth--
                else -> if (depth == 0) when {
                    tokens.equals(
                        j0, "fun", "val", "var", "lateinit",
                        "public", "private", "protected", "class", "interface"
                    ) -> {
                        return j0
                    }
                    // enum class, data class, private class... these depend on the work after them...
                    tokens.equals(j0 + 1, "class") &&
                            tokens.equals(j0, "data", "enum", "value", "inner") -> {
                        return j0
                    }
                }
            }
        }
        return tokens.size
    }

    private fun readLazyValue(): Expression {
        val end = findLazyValueEnd()
        return pushScope(ScopeType.METHOD_BODY, "body") { scope ->
            val tokens1 = TokenSubList(tokens, i, end, imports)
            val expr = LazyExpression(tokens1, scope, origin(i))
            i = end
            expr
        }
    }

    private fun readSelfType(end: Int): Type {
        check(tokens.equals(end - 2, ".")) {
            "Expected period for field with receiver type at ${tokens.err(end - 2)}"
        }

        val type = tokens.push(end - 2) {
            readType(null, true)!!
        }

        consume(".")

        return type
    }

    private fun findParameterStart(): Int {
        // val x, var A<>.x
        // val x: Int, val x = 0
        //  end symbols: [:, =, get(), set(), public, private, protected]
        var end = i
        var depth = 0
        findFieldEnd@ while (end < tokens.size) {
            val j0 = end++
            when (tokens.getType(j0)) {
                TokenType.OPEN_CALL -> {
                    if (depth == 0) {
                        end--
                        break@findFieldEnd
                    } else depth++
                }
                TokenType.OPEN_ARRAY, TokenType.OPEN_BLOCK -> depth++
                TokenType.CLOSE_CALL, TokenType.CLOSE_ARRAY, TokenType.CLOSE_BLOCK -> depth--
                else -> when {
                    tokens.equals(j0, "<") -> depth++
                    tokens.equals(j0, ">") -> depth--
                }
            }
            check(depth >= 0) { "Invalid depth @${tokens.err(i)}" }
        }
        return end
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
        val parameters = ArrayList<Parameter>()
        pushCall {
            while (i < tokens.size) {
                // todo comptime name: type
                val isVararg = consumeIf("vararg")
                val paramOrigin = origin(i)
                val name = consumeName(VSCodeType.PARAMETER, 0)
                consume(":")
                val type = readType(selfType, true)
                    ?: throw IllegalStateException("Missing type at ${tokens.err(i)}")
                val defaultValue = if (tokens.equals(i, "=")) {
                    readLazyValue()
                } else null
                val parameter = Parameter(
                    parameters.size, false, true, isVararg,
                    name, type, defaultValue,
                    currPackage, paramOrigin
                )
                parameters.add(parameter)
                readComma()
            }
        }
        return parameters
    }

    override fun readMethodBody(): ExpressionList {
        throw NotImplementedError()
    }
}