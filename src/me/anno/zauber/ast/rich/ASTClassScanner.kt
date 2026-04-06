package me.anno.zauber.ast.rich

import me.anno.langserver.VSCodeModifier
import me.anno.langserver.VSCodeType
import me.anno.zauber.Compile.root
import me.anno.zauber.ast.FlagSet
import me.anno.zauber.ast.rich.FieldGetterSetter.createBackingField
import me.anno.zauber.ast.rich.FieldGetterSetter.createGetterMethod
import me.anno.zauber.ast.rich.FieldGetterSetter.createSetterMethod
import me.anno.zauber.ast.rich.FieldGetterSetter.createValueField
import me.anno.zauber.ast.rich.FieldGetterSetter.finishField
import me.anno.zauber.ast.rich.FieldGetterSetter.needsGetter
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.rich.WhereConditions.readWhereConditions
import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.expression.unresolved.AssignmentExpression
import me.anno.zauber.ast.rich.expression.unresolved.FieldExpression
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.scope.lazy.LazyExpression
import me.anno.zauber.scope.lazy.TokenSubList
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.typeresolution.TypeResolution.getSelfType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import kotlin.math.max

/**
 * to make type-resolution immediately available/resolvable
 * */
abstract class ASTClassScanner(tokens: TokenList) : ZauberASTBuilderBase(tokens, root, true) {

    companion object {
        private val classPrefixes = arrayOf("data", "enum", "value", "inner")
        private val notValueKeywords = arrayOf(
            "fun", "val", "var", "lateinit",
            "public", "private", "protected", "interface",
            "package", "import", "companion",
            "open", "abstract", "override", "operator",
            "get", "set", "typealias", "external",
            "constructor"
        )
    }

    open fun skipAnnotations() {
        if (tokens.equals(i, "@") && tokens.equals(i + 1, TokenType.NAME)) {
            i += 2
            while (tokens.equals(i, ".") && tokens.equals(i + 1, TokenType.NAME)) {
                i += 2
            }
            skipValueParameters()
        }
    }

    fun pushNamedScopeLazy(
        name: String,
        listenType: Int,
        scopeType: ScopeType,
        readLazily: (scope: Scope, readBody: Boolean) -> Unit
    ) {
        val i0 = i
        val i1 = tokens.size
        val parentScope = currPackage

        val classScope = parentScope.getOrPut(name, scopeType)
        classScope.addKeywords(listenType or packFlags())
        classScope.fileName = tokens.fileName
        classScope.initParts += {

            // store old state
            val prevPackage = currPackage
            val prevSize = tokens.size
            val prevI = i

            // set original state
            i = i0
            tokens.size = i1
            currPackage = parentScope

            readLazily(classScope, true)

            // restore state
            currPackage = prevPackage
            tokens.size = prevSize
            i = prevI
        }

        readLazily(classScope, false)
    }

    open fun foundNamedScope(name: String, listenType: FlagSet, scopeType: ScopeType) {
        val origin = origin(i - 1)
        pushNamedScopeLazy(name, listenType, scopeType) { classScope, readBody ->
            val typeParameters = readTypeParameterDeclarations(classScope)
            classScope.typeParameters = typeParameters
            classScope.hasTypeParameters = true

            if (consumeIf("private")) {
                addFlag(Flags.PRIVATE)
                consume("constructor")
            } else if (consumeIf("protected")) {
                addFlag(Flags.PROTECTED)
                consume("constructor")
            } else consumeIf("constructor")

            if (readBody) {
                val constrOrigin = origin(i)
                val constructorScope = classScope.getOrCreatePrimaryConstructorScope()
                constructorScope.addKeywords(packFlags())
                pushScope(constructorScope) {
                    val selfType = classScope.typeWithArgs
                    var valueParameters = if (tokens.equals(i, TokenType.OPEN_CALL)) {
                        readParameterDeclarations(selfType)
                    } else emptyList()

                    if (scopeType == ScopeType.ENUM_CLASS) {
                        val param0 = Parameter(0, "ordinal", Types.IntType, constructorScope, constrOrigin)
                        val param1 = Parameter(1, "name", Types.StringType, constructorScope, constrOrigin)
                        param0.getOrCreateField(null, Flags.SYNTHETIC)
                        param1.getOrCreateField(null, Flags.SYNTHETIC)
                        valueParameters = listOf(param0, param1) + valueParameters.map { it.shift(2) }
                    }

                    val instr = ArrayList<Expression>()
                    for (param in valueParameters) {
                        if (param.isVal || param.isVar) {
                            val paramField = param.field!!
                            val initialValue = FieldExpression(paramField, constructorScope, param.origin)
                            val classField = classScope.addField(
                                null, false, param.isVar,
                                null, param.name, param.type, initialValue,
                                paramField.flags, param.origin
                            )
                            finishField(classField)

                            // todo is this our job? compare with ZauberASTBuilder
                            instr.add(
                                AssignmentExpression(
                                    FieldExpression(classField, constructorScope, param.origin),
                                    initialValue
                                )
                            )
                        }
                    }

                    constructorScope.selfAsConstructor = Constructor(
                        valueParameters, constructorScope,
                        null, ExpressionList(instr, constructorScope, constrOrigin), flags, constrOrigin
                    )
                }
            } else if (tokens.equals(i, TokenType.OPEN_CALL)) {
                // skip constructor params
                skipCall()
                packFlags()
            }

            if (consumeIf(":")) {
                collectSuperNames(classScope)
            }

            if (scopeType == ScopeType.OBJECT || scopeType == ScopeType.COMPANION_OBJECT) {
                classScope.getOrCreateObjectField(origin)
            }

            readClassBody(classScope, readBody)
            popGenericParams()
        }
    }

    fun readClassBody(classScope: Scope, readBody: Boolean) {
        if (readBody) readClassBody(classScope)
        else if (tokens.equals(i, TokenType.OPEN_BLOCK)) skipBlock()
    }

    fun collectSuperNames(classScope: Scope) {
        do {
            val type = readTypeNotNull(classScope.typeWithArgs, true)
            val valueParameters = if (tokens.equals(i, TokenType.OPEN_CALL)) {
                readValueParameters()
            } else null
            val delegate = if (consumeIf("by")) readLazyValue() else null
            classScope.superCalls.add(SuperCall(type, valueParameters, delegate))
        } while (consumeIf(TokenType.COMMA))
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

    fun skipCall() {
        i = tokens.findBlockEnd(i, TokenType.OPEN_CALL, TokenType.CLOSE_CALL) + 1
    }

    override fun readFileLevel() {
        while (i < tokens.size) {
            val i0 = i
            when (tokens.getType(i)) {
                TokenType.OPEN_BLOCK, TokenType.OPEN_CALL, TokenType.OPEN_ARRAY,
                TokenType.CLOSE_CALL, TokenType.CLOSE_ARRAY, TokenType.CLOSE_BLOCK ->
                    throw IllegalStateException("Unexpected token ${tokens.err(i)}")
                else -> collectNamesOnDepth0()
            }
            i = max(i0 + 1, i)
        }
    }

    open fun readPackage() {
        val (path, ni) = tokens.readPath(i)
        currPackage = path
        path.mergeScopeTypes(ScopeType.PACKAGE)
        path.typeParameters = emptyList()
        path.hasTypeParameters = true
        i = ni
    }

    open fun readImport() {
        val (path, ni) = tokens.readImport(i)
        imports.add(path)
        i = ni
    }

    open fun checkImports(): Boolean {
        throw NotImplementedError()
    }

    open fun collectNamesOnDepth0() {
        // to do switch is probably faster...
        when {
            consumeIf("package") -> readPackage()
            consumeIf("import") -> readImport()
            consumeIf("typealias") -> readTypeAlias()
            consumeIf("var") || consumeIf("val") -> readField()
            consumeIf("fun") -> {
                if (consumeIf("interface")) {
                    val name = consumeName(VSCodeType.INTERFACE, VSCodeModifier.DECLARATION.flag)
                    foundNamedScope(name, Flags.FUN_INTERFACE, ScopeType.INTERFACE)
                } else readMethod()
            }
            consumeIf("constructor") -> readConstructor()
            consumeIf("external") -> addFlag(Flags.EXTERNAL)
            consumeIf("override") -> addFlag(Flags.OVERRIDE)
            consumeIf("public") -> addFlag(Flags.PUBLIC)
            consumeIf("protected") -> addFlag(Flags.PROTECTED)
            consumeIf("private") -> addFlag(Flags.PRIVATE)
            consumeIf("abstract") -> addFlag(Flags.ABSTRACT)
            consumeIf("operator") -> addFlag(Flags.OPERATOR)
            consumeIf("open") -> addFlag(Flags.OPEN)
            consumeIf("sealed") -> addFlag(Flags.SEALED)
            consumeIf("tailrec") -> {}// addKeyword(Keywords.TAILREC)
            consumeIf("lateinit") -> addFlag(Flags.LATEINIT)
            consumeIf("inline") -> addFlag(Flags.INLINE)
            consumeIf("crossinline") -> addFlag(Flags.CROSS_INLINE)
            consumeIf("const") -> {
                addFlag(Flags.CONSTEXPR)
                readField()
            }
            consumeIf("infix") -> addFlag(Flags.INFIX)
            consumeIf("external") -> addFlag(Flags.EXTERNAL)
            consumeIf(";") -> {}
            consumeIf("init") -> {
                currPackage.primaryConstructorScope!!
                    .code.add(readLazyBody())
            }
            else -> checkForTypes()
        }
    }

    private fun readSelfTypeIfPresent(end: Int): Type? {
        val nameStart = i
        check(tokens.equals(end - 1, TokenType.NAME, TokenType.KEYWORD)) {
            "Expected name at ${tokens.err(end - 1)}"
        }
        check(end > nameStart) { "Expected name at ${tokens.err(nameStart)}" }
        return if (nameStart < end - 1) readSelfType(end) else null
    }

    open fun readField() {
        val origin = origin(i - 1)
        val isMutable = tokens.equals(i - 1, "var")
        val end = findFieldNameEnd()
        val name = tokens.toString(end - 1)

        val ownerScope = currPackage
        val fieldScope = ownerScope.generate(name, origin, ScopeType.FIELD)
        fieldScope.addKeywords(packFlags())
        pushScope(fieldScope) {
            val typeParameters = readTypeParameterDeclarations(fieldScope)
            fieldScope.typeParameters = typeParameters
            fieldScope.hasTypeParameters = true

            val selfType0 = readSelfTypeIfPresent(end)
            val selfType = selfType0 ?: getSelfType(ownerScope)
            // if (selfType != null) selfType = selfType.resolve()

            val name = consumeName(VSCodeType.PROPERTY, VSCodeModifier.DECLARATION.flag)

            var valueType = if (consumeIf(":")) readTypeNotNull(selfType, true) else null
            val initialValue =
                if (consumeIf("=")) {
                    readLazyValue()
                } else if (fieldScope.flags.hasFlag(Flags.LATEINIT)) {
                    SpecialValueExpression(SpecialValue.NULL, ownerScope, origin)
                } else null

            val getterVisibility = readVisibility()
            var setterVisibility = getterVisibility
            var getterOrigin = origin
            val getterBody: Expression? = if (consumeIf("get")) {
                getterOrigin = origin(i - 1)
                val body = if (consumeIf(TokenType.OPEN_CALL)) {
                    consume(TokenType.CLOSE_CALL)
                    tryReadBody(fieldScope)
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
                        readTypeNotNull(null, true)
                    } else null
                    if (valueType == null) valueType = setterType

                    consume(TokenType.CLOSE_CALL)
                    tryReadBody(fieldScope)
                } else null
            } else null

            val field = ownerScope.addField(
                selfType0, selfType0 != null, isMutable, null,
                name, valueType, initialValue, fieldScope.flags, origin
            )
            field.typeParameters = typeParameters
            fieldScope.selfAsField = field

            if (initialValue != null) {
                val constr = ownerScope.getOrCreatePrimaryConstructorScope()
                val fieldExpr = FieldExpression(field, ownerScope, origin)
                constr.code.add(AssignmentExpression(fieldExpr, initialValue))
            }

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

            field.getter?.addKeywords(getterVisibility)
            field.setter?.addKeywords(setterVisibility)
            popGenericParams()
        }
    }

    private fun tryReadBody(fieldScope: Scope): Expression {
        return if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
            readLazyBody()
        } else if (consumeIf("=")) {
            val originI = origin(i - 1)
            ReturnExpression(readLazyValue(), null, fieldScope, originI)
        } else throw IllegalStateException("Expected body for getter, got neither = nor { at ${tokens.err(i)}")
    }

    private fun readVisibility(): Int {
        var flags = 0
        while (i < tokens.size) {
            when {
                consumeIf("public") -> {}
                consumeIf("private") -> flags = flags or Flags.PRIVATE
                consumeIf("protected") -> flags = flags or Flags.PROTECTED
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
        constrScope.typeParameters = emptyList()
        constrScope.hasTypeParameters = true
        constrScope.flags = constrScope.flags or packFlags()

        pushScope(constrScope) {
            val selfType = classScope.typeWithArgs
            val valueParameters = readParameterDeclarations(selfType)
            val superCall = if (consumeIf(":")) readInnerSuperCall() else null

            val body = if (tokens.equals(i, TokenType.OPEN_BLOCK)) readLazyBody() else null
            constrScope.selfAsConstructor = Constructor(
                valueParameters,
                constrScope, superCall, body,
                constrScope.flags, origin
            )
        }
    }

    private fun readInnerSuperCall(): InnerSuperCall {
        val origin = origin(i)
        val type = when {
            consumeIf("this") -> InnerSuperCallTarget.THIS
            consumeIf("super") -> InnerSuperCallTarget.SUPER
            else -> throw IllegalStateException("Expected this() or super() at ${tokens.err(i)}")
        }
        val values = readValueParameters()
        return InnerSuperCall(type, values, origin)
    }

    open fun readMethod() {
        val origin = origin(i - 1)

        val end = findParameterStart()
        val name = tokens.toString(end - 1)
        val ownerScope = currPackage
        val methodScope = ownerScope.generate(name, origin, ScopeType.METHOD)
        methodScope.flags = methodScope.flags or packFlags()

        pushScope(methodScope) {
            val typeParameters = readTypeParameterDeclarations(methodScope)
            methodScope.typeParameters = typeParameters
            methodScope.hasTypeParameters = true

            val selfType0 = readSelfTypeIfPresent(end)
            var selfType = selfType0 ?: getSelfType(ownerScope)
            if (selfType != null) selfType = selfType.resolve()

            val name = consumeName(VSCodeType.METHOD, VSCodeModifier.DECLARATION.flag)

            val valueParameters = readParameterDeclarations(selfType)
            val whereConditions = readWhereConditions()

            val returnType = if (consumeIf(":")) {
                readTypeNotNull(selfType, true)
            } else if (tokens.equals(i, "{") ||
                methodScope.flags.hasFlag(Flags.EXTERNAL)
            ) { // type is implicitly Unit
                Types.UnitType
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
                selfType, selfType0 != null, name,
                typeParameters, valueParameters,
                methodScope, returnType, whereConditions, body,
                methodScope.flags, origin
            )

            popGenericParams()
        }
    }

    private fun readLazyBody(): Expression {
        return pushBlock(ScopeType.METHOD_BODY, "body") { scope ->
            val tokens1 = TokenSubList(tokens, i, tokens.size)
            val expr = LazyExpression(tokens1, true, scope, origin(i), imports, genericParams.last())
            scope.initParts += { expr.value } // load expression contents, if we need them
            i = tokens.size
            expr
        }
    }

    private fun readLazyValue(): Expression {
        val end = findLazyValueEnd()
        check(i < end) { "Lazy value must not be empty, @${tokens.err(i)}" }
        return pushScope(ScopeType.METHOD_BODY, "body") { scope ->
            val tokens1 = TokenSubList(tokens, i, end)
            val expr = LazyExpression(tokens1, false, scope, origin(i), imports, genericParams.last())
            scope.initParts += { expr.value } // load expression contents, if we need them
            i = end
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
                TokenType.CLOSE_CALL, TokenType.CLOSE_ARRAY, TokenType.CLOSE_BLOCK -> {
                    if (depth == 0) return end
                    depth--
                }
                TokenType.COMMA, TokenType.SEMICOLON -> if (depth == 0) return j0
                else -> if (depth == 0) when {
                    tokens.equals(j0, ".", "+", "&&", "||", "-") -> end++ // skip another one
                    tokens.equals(j0, *notValueKeywords) -> return j0
                    tokens.equals(j0, "object") && !tokens.equals(j0, ":") -> return j0
                    // enum class, data class, private class... these depend on the work after them...
                    tokens.equals(j0 + 1, "class") && tokens.equals(j0, *classPrefixes) -> return j0
                    tokens.equals(j0, "class") && !tokens.equals(j0 - 1, "::") -> return j0
                }
            }
        }
        return tokens.size
    }

    private fun readSelfType(end: Int): Type {
        check(tokens.equals(end - 2, ".")) {
            "Expected period for field with receiver type at ${tokens.err(end - 2)}"
        }

        val type = tokens.push(end - 2) {
            readTypeNotNull(null, true)
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
            consumeIf("enum") -> {
                consume("class")
                val name = consumeName(VSCodeType.ENUM, VSCodeModifier.DECLARATION.flag)
                foundNamedScope(name, Flags.NONE, ScopeType.ENUM_CLASS)
            }

            consumeIf("inner") -> {
                consume("class")
                val name = consumeName(VSCodeType.CLASS, VSCodeModifier.DECLARATION.flag)
                foundNamedScope(name, Flags.NONE, ScopeType.INNER_CLASS)
            }

            consumeIf("data") -> {
                consume("class")
                val name = consumeName(VSCodeType.CLASS, VSCodeModifier.DECLARATION.flag)
                foundNamedScope(name, Flags.DATA_CLASS, ScopeType.NORMAL_CLASS)
            }

            consumeIf("value") -> {
                when {
                    consumeIf("class") -> {
                        val name = consumeName(VSCodeType.CLASS, VSCodeModifier.DECLARATION.flag)
                        foundNamedScope(name, Flags.VALUE, ScopeType.NORMAL_CLASS)
                    }
                    consumeIf("val") -> {
                        addFlag(Flags.VALUE)
                        readField()
                    }
                    consumeIf("var") -> {
                        addFlag(Flags.VALUE)
                        readField()
                    }
                    else -> throw IllegalStateException("Expected class, val or var after 'value' at ${tokens.err(i)}")
                }
            }

            consumeIf("class") -> {
                check(!tokens.equals(i - 2, "::"))
                val name = consumeName(VSCodeType.CLASS, VSCodeModifier.DECLARATION.flag)
                foundNamedScope(name, Flags.NONE, ScopeType.NORMAL_CLASS)
            }

            tokens.equals(i, "object") && !tokens.equals(i - 1, "companion")
                    && !tokens.equals(i + 1, ":") -> {
                i++ // skip 'object'
                val name = consumeName(VSCodeType.CLASS, VSCodeModifier.DECLARATION.flag)
                foundNamedScope(name, Flags.NONE, ScopeType.OBJECT)
            }

            consumeIf("companion") -> {
                consume("object")
                val name = if (tokens.equals(i, TokenType.NAME, TokenType.KEYWORD)) {
                    consumeName(VSCodeType.CLASS, VSCodeModifier.DECLARATION.flag)
                } else "Companion"
                foundNamedScope(name, Flags.NONE, ScopeType.COMPANION_OBJECT)
            }

            consumeIf("interface") -> {
                val name = consumeName(VSCodeType.INTERFACE, VSCodeModifier.DECLARATION.flag)
                val keywords = if (tokens.equals(i - 2, "fun")) Flags.FUN_INTERFACE else Flags.NONE
                foundNamedScope(name, keywords, ScopeType.INTERFACE)
            }

            consumeIf("typealias") -> {
                val name = consumeName(VSCodeType.TYPE, VSCodeModifier.DECLARATION.flag)
                foundNamedScope(name, Flags.NONE, ScopeType.TYPE_ALIAS)
            }

            else -> throw IllegalStateException("Unknown token ${tokens.toString(i)}")
        }
    }

    override fun readSuperCalls(classScope: Scope) {
        throw NotImplementedError()
    }

    override fun readExpression(minPrecedence: Int): Expression = readLazyValue()

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
                var flags = Flags.NONE

                while (true) {
                    flags = flags or when {
                        consumeIf("public") -> Flags.PUBLIC
                        consumeIf("protected") -> Flags.PROTECTED
                        consumeIf("private") -> Flags.PRIVATE
                        consumeIf("open") -> Flags.OPEN
                        consumeIf("override") -> Flags.OVERRIDE
                        else -> break
                    }
                }

                val isVararg = consumeIf("vararg")
                val isVal = consumeIf("val")
                val isVar = consumeIf("var")

                val paramOrigin = origin(i)
                val name = consumeName(VSCodeType.PARAMETER, 0)
                consume(":")

                var type = readTypeNotNull(selfType, true)

                val defaultValue = if (consumeIf("=")) readLazyValue() else null

                if (isVararg) type = Types.ArrayType.withTypeParameter(type)
                val parameter = Parameter(
                    parameters.size, isVar, isVal, isVararg,
                    name, type, defaultValue,
                    currPackage, paramOrigin
                )
                parameters.add(parameter)

                val size = tokens.size
                parameter.getOrCreateField(null, flags)
                check(size == tokens.size) { "Token size changed" }

                readComma()
            }
        }
        return parameters
    }

    override fun readMethodBody(): ExpressionList {
        throw NotImplementedError()
    }
}