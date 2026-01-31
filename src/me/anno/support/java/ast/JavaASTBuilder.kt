package me.anno.support.java.ast

import me.anno.langserver.VSCodeModifier
import me.anno.langserver.VSCodeType
import me.anno.zauber.ZauberLanguage
import me.anno.zauber.ast.KeywordSet
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.Annotation
import me.anno.zauber.ast.rich.DataClassGenerator.finishDataClass
import me.anno.zauber.ast.rich.Keywords.hasFlag
import me.anno.zauber.ast.rich.ScopeSplit.shouldSplitIntoSubScope
import me.anno.zauber.ast.rich.ScopeSplit.splitIntoSubScope
import me.anno.zauber.ast.rich.controlflow.*
import me.anno.zauber.ast.rich.expression.*
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.ast.rich.expression.resolved.ThisExpression
import me.anno.zauber.ast.rich.expression.unresolved.*
import me.anno.zauber.ast.rich.expression.unresolved.AssignIfMutableExpr.Companion.plusAssignName
import me.anno.zauber.ast.rich.expression.unresolved.AssignIfMutableExpr.Companion.plusName
import me.anno.zauber.ast.rich.expression.unresolved.MemberNameExpression.Companion.nameExpression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.typeresolution.CallWithNames.createArrayOfExpr
import me.anno.zauber.types.BooleanUtils.not
import me.anno.zauber.types.Import
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.AnyType
import me.anno.zauber.types.Types.ArrayType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.ListType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.Types.UnitType
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.SelfType
import kotlin.math.max
import kotlin.math.min

// todo lots and lots are shared between Kotlin and Java or C, maybe we can reuse many methods
class JavaASTBuilder(
    tokens: TokenList, root: Scope,
    val language: ZauberLanguage = ZauberLanguage.ZAUBER
) : ZauberASTBuilderBase(tokens, root, false) {

    companion object {

        private val LOGGER = LogManager.getLogger(JavaASTBuilder::class)

        var debug = false

        val unitInstance by lazy {
            val scope = UnitType.clazz
            if (scope.objectField == null) scope.objectField = scope.addField(
                null, false, isMutable = false, null,
                scope.name, scope.typeWithArgs, null, Keywords.NONE, -1
            )
            FieldExpression(scope.objectField!!, scope, -1)
        }
    }

    val lsTypes = IntArray(tokens.size).apply { fill(-1) }
    val lsModifiers = IntArray(tokens.size)

    fun setLSType(i: Int, type: VSCodeType, modifiers: Int) {
        lsTypes[i] = type.ordinal
        lsModifiers[i] = modifiers
    }

    init {
        // numbers and strings are trivial to fill
        for (i in 0 until tokens.size) {
            lsTypes[i] = when (tokens.getType(i)) {
                TokenType.NUMBER -> VSCodeType.NUMBER
                TokenType.STRING -> VSCodeType.STRING
                TokenType.SYMBOL -> VSCodeType.OPERATOR
                TokenType.KEYWORD -> VSCodeType.KEYWORD
                else -> continue
            }.ordinal
        }
    }

    // todo assign them appropriately
    val annotations = ArrayList<Annotation>()

    private fun readClass(scopeType: ScopeType) {
        val origin = origin(i)
        val name = consumeName(VSCodeType.CLASS, VSCodeModifier.DECLARATION.flag)

        val keywords = packKeywords()
        val classScope = currPackage.getOrPut(name, tokens.fileName, scopeType)

        val typeParameters = readTypeParameterDeclarations(classScope)
        classScope.typeParameters = typeParameters
        classScope.hasTypeParameters = true

        val privatePrimaryConstructor = consumeIf("private")

        readAnnotations()

        consumeIf("constructor")

        val constructorOrigin = origin(i)
        val constructorParams = readPrimaryConstructorParameters(classScope)
        val constructorBody = createAssignmentInstructionsForPrimaryConstructor(
            classScope, constructorParams, constructorOrigin
        )

        readSuperCalls(classScope)

        val primConstructorScope = classScope.getOrCreatePrimConstructorScope()
        val primarySuperCall = classScope.superCalls.firstOrNull { it.valueParameters != null }
        val primaryConstructor = Constructor(
            constructorParams ?: emptyList(),
            primConstructorScope, if (primarySuperCall != null) {
                InnerSuperCall(InnerSuperCallTarget.SUPER, primarySuperCall.valueParameters!!, origin)
            } else null, constructorBody,
            if (privatePrimaryConstructor) Keywords.PRIVATE else Keywords.NONE,
            constructorOrigin
        )
        primConstructorScope.selfAsConstructor = primaryConstructor

        readClassBody(name, keywords, scopeType)
        popGenericParams()
    }

    private fun createAssignmentInstructionsForPrimaryConstructor(
        classScope: Scope, constructorParams: List<Parameter>?,
        constructorOrigin: Int
    ): ExpressionList {
        val result = ArrayList<Expression>()
        val scope = classScope.getOrCreatePrimConstructorScope()
        if (constructorParams != null) {
            for (parameter in constructorParams) {
                if (!(parameter.isVal || parameter.isVar)) continue

                val origin = parameter.origin
                val parameterField = parameter.getOrCreateField(null, Keywords.NONE)
                val classField = classScope.addField(
                    classScope.typeWithArgs, false, isMutable = parameter.isVar,
                    parameter, parameter.name, parameter.type, null, Keywords.SYNTHETIC, origin
                )
                val dstExpr = DotExpression(
                    ThisExpression(classScope, scope, origin), null,
                    FieldExpression(classField, scope, origin),
                    scope, origin
                )
                val srcExpr = FieldExpression(parameterField, scope, origin)
                result.add(AssignmentExpression(dstExpr, srcExpr))
            }
        }
        return ExpressionList(result, scope, constructorOrigin)
    }

    private fun readPrimaryConstructorParameters(classScope: Scope): List<Parameter>? {
        val scopeType = classScope.scopeType
        val constructorOrigin = origin(i)
        return if (tokens.equals(i, TokenType.OPEN_CALL)) {
            val constructorScope = classScope.getOrCreatePrimConstructorScope()
            var parameters = pushScope(constructorScope) {
                val selfType = ClassType(classScope, null)
                pushCall { readParameterDeclarations(selfType) }
            }
            if (scopeType == ScopeType.ENUM_CLASS) {
                parameters = listOf(
                    Parameter(0, "ordinal", IntType, constructorScope, constructorOrigin),
                    Parameter(1, "name", StringType, constructorScope, constructorOrigin)
                ) + parameters.map { it.shift(2) }
            }
            parameters
        } else if (scopeType == ScopeType.ENUM_CLASS) {
            val constructorScope = classScope.getOrCreatePrimConstructorScope()
            val parameters = listOf(
                Parameter(0, "ordinal", IntType, constructorScope, constructorOrigin),
                Parameter(1, "name", StringType, constructorScope, constructorOrigin)
            )
            parameters
        } else null
    }

    private fun readInterface() {
        val name = consumeName(VSCodeType.INTERFACE, VSCodeModifier.DECLARATION.flag)
        val clazz = currPackage.getOrPut(name, tokens.fileName, ScopeType.INTERFACE)
        val keywords = packKeywords()
        clazz.typeParameters = readTypeParameterDeclarations(clazz)
        clazz.hasTypeParameters = true

        readSuperCalls(clazz)
        readClassBody(name, keywords, ScopeType.INTERFACE)
        popGenericParams()
    }

    private fun readAnnotations() {
        if (consumeIf("@")) {
            annotations.add(readAnnotation())
        }
    }

    private fun readSuperCalls(classScope: Scope) {
        if (consumeIf("extends")) {
            do {
                val type = readTypeNotNull(null, true) as ClassType
                classScope.superCalls.add(SuperCall(type, emptyList(), null))
            } while (consumeIf(","))
        }

        if (consumeIf("implements")) {
            do {
                val type = readTypeNotNull(null, true) as ClassType
                classScope.superCalls.add(SuperCall(type, null, null))
            } while (consumeIf(","))
        }

        val addAnyIfEmpty = classScope != AnyType.clazz
        if (addAnyIfEmpty && classScope.superCalls.isEmpty()) {
            classScope.superCalls.add(SuperCall(AnyType, emptyList(), null))
        }
    }

    private fun readClassBody(name: String, keywords: KeywordSet, scopeType: ScopeType): Scope {
        val classScope = currPackage.getOrPut(name, tokens.fileName, scopeType)
        classScope.keywords = classScope.keywords or keywords

        if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
            pushBlock(classScope) {
                if (classScope.scopeType == ScopeType.ENUM_CLASS) {
                    val endIndex = readEnumBody()
                    i = min(endIndex + 1, tokens.size) // skipping over semicolon
                }
                readFileLevel()
            }
        }

        if (keywords.hasFlag(Keywords.DATA_CLASS) || keywords.hasFlag(Keywords.VALUE)) {
            pushScope(classScope) {
                finishDataClass(classScope)
            }
        }

        return classScope
    }

    private fun readEnumBody(): Int {

        val origin0 = origin(i)
        var endIndex = tokens.findToken(i, ";")
        if (endIndex < 0) endIndex = tokens.size
        val enumScope = currPackage
        val companionScope = enumScope.getOrPut("Companion", ScopeType.COMPANION_OBJECT)
        // val needsPrimaryConstructor = companionScope.primaryConstructorScope == null
        val primaryConstructorScope = companionScope.getOrCreatePrimConstructorScope()
        primaryConstructorScope.selfAsConstructor = Constructor(
            emptyList(), primaryConstructorScope,
            null, ExpressionList(ArrayList(), primaryConstructorScope, origin0),
            Keywords.NONE, origin0
        )

        push(endIndex) {
            var ordinal = 0
            while (i < tokens.size) {
                // read enum value
                readAnnotations()

                val origin = origin(i)
                val name = consumeName(VSCodeType.ENUM_MEMBER, 0)

                val typeParameters = readTypeParameters(null)
                val valueParameters = if (tokens.equals(i, TokenType.OPEN_CALL)) {
                    pushCall { readParameterExpressions() }
                } else emptyList()

                val keywords = packKeywords()
                val entryScope = readClassBody(name, Keywords.NONE, ScopeType.ENUM_ENTRY_CLASS)
                // todo add name and id as parameters
                val extraValueParameters = listOf(
                    NamedParameter(null, NumberExpression((ordinal++).toString(), companionScope, origin)),
                    NamedParameter(null, StringExpression(name, companionScope, origin)),
                )
                val initialValue = ConstructorExpression(
                    enumScope, typeParameters,
                    extraValueParameters + valueParameters,
                    null, enumScope, origin
                )
                val valueType =
                    if (enumScope.typeParameters.isNotEmpty()) null // we need to resolve them
                    else enumScope.typeWithArgs
                val field = companionScope.addField(
                    companionScope.typeWithoutArgs,
                    false, isMutable = false, null,
                    name, valueType, initialValue, keywords, origin
                )
                entryScope.objectField = field

                val fieldExpr = FieldExpression(field, primaryConstructorScope, origin)
                primaryConstructorScope.code.add(AssignmentExpression(fieldExpr, initialValue))

                readComma()
            }
        }

        createEnumProperties(companionScope, enumScope, origin0)
        return endIndex
    }

    private fun createEnumProperties(companionScope: Scope, enumScope: Scope, origin: Int) {

        companionScope.hasTypeParameters = true

        val constructorScope = companionScope.getOrCreatePrimConstructorScope()
        val listType = ClassType(ListType.clazz, listOf(enumScope.typeWithoutArgs))
        val entryValues = enumScope.enumEntries.map { entryScope ->
            val field = entryScope.objectField!!
            FieldExpression(field, constructorScope, origin)
        }
        val initialValue = createArrayOfExpr(enumScope.typeWithoutArgs, entryValues, constructorScope, origin)

        val entriesField = constructorScope.addField(
            companionScope.typeWithoutArgs,
            false, isMutable = false, null,
            "entries", listType,
            initialValue, Keywords.SYNTHETIC, origin
        )

        val entriesFieldExpr = FieldExpression(entriesField, constructorScope, origin)
        constructorScope.code.add(AssignmentExpression(entriesFieldExpr, initialValue))
    }

    private fun applyImport(import: Import) {
        imports.add(import)
        if (import.allChildren) {
            for (child in import.path.children) {
                currPackage.imports + Import2(child.name, child, false)
            }
        } else {
            currPackage.imports + Import2(import.name, import.path, true)
        }
    }

    fun readFileLevel() {
        loop@ while (i < tokens.size) {
            if (LOGGER.isDebugEnabled) LOGGER.debug("readFileLevel[$i]: ${tokens.err(i)}")
            when {
                consumeIf("package") -> {
                    val (path, nextI) = tokens.readPath(i)
                    for (k in i until nextI) {
                        if (tokens.equals(k, TokenType.NAME)) {
                            setLSType(k, VSCodeType.NAMESPACE, 0)
                        }
                    }
                    currPackage = path
                    currPackage.mergeScopeTypes(ScopeType.PACKAGE)
                    i = nextI
                }

                consumeIf("import") -> {
                    val (import, nextI) = tokens.readImport(i)
                    // todo if there is an 'as', that is a type/variable, not a namespace
                    for (k in i until nextI) {
                        if (tokens.equals(k, TokenType.NAME)) {
                            setLSType(k, VSCodeType.NAMESPACE, 0)
                        }
                    }
                    i = nextI
                    applyImport(import)
                }

                consumeIf("enum") -> {
                    consume("class")
                    readClass(ScopeType.ENUM_CLASS)
                }

                consumeIf("class") -> readClass(ScopeType.NORMAL_CLASS)

                // todo methods / fields need some read-ahead
                /*consumeIf("fun") -> {
                    if (consumeIf("interface")) {
                        keywords = keywords or Keywords.FUN_INTERFACE
                        readInterface()
                    } else {
                        readMethod()
                    }
                }
                consumeIf("var") -> readFieldInClass(true)
                consumeIf("val") -> readFieldInClass(false)*/
                // todo it is a constructor, if the method name = class name

                consumeIf("interface") -> readInterface()

                tokens.equals(i, "static") && tokens.equals(i + 1, TokenType.OPEN_BLOCK) -> {
                    i++
                    // static init block
                    // todo first get companion object scope...
                    pushBlock(currPackage.getOrCreatePrimConstructorScope()) {
                        readMethodBody()
                    }
                }

                tokens.equals(i, TokenType.OPEN_BLOCK) -> {
                    // init block
                    pushBlock(currPackage.getOrCreatePrimConstructorScope()) {
                        readMethodBody()
                    }
                }

                consumeIf("@") -> annotations.add(readAnnotation())

                tokens.equals(i, TokenType.NAME) || tokens.equals(i, TokenType.KEYWORD) ->
                    collectKeywords()

                consumeIf(";") -> {}// just skip it

                else -> throw NotImplementedError("Unknown token at ${tokens.err(i)}")
            }
        }
    }

    fun readAnnotation(): Annotation {
        if (tokens.equals(i, TokenType.NAME) &&
            tokens.equals(i + 1, ":") &&
            tokens.equals(i + 2, TokenType.NAME)
        ) {
            // skipping scope
            i += 2
        }
        check(tokens.equals(i, TokenType.NAME))
        val path = readTypePath(null)
            ?: throw IllegalStateException("Expected type for annotation at ${tokens.err(i)}")
        val params = if (tokens.equals(i, TokenType.OPEN_CALL)) {
            pushCall { readParameterExpressions() }
        } else emptyList()
        return Annotation(path, params)
    }

    fun collectKeywords() {
        if (!tokens.equals(i, TokenType.STRING)) {
            keywords = keywords or when {
                consumeIf("public") -> Keywords.PUBLIC
                consumeIf("private") -> Keywords.PRIVATE
                consumeIf("native") -> Keywords.EXTERNAL
                consumeIf("override") -> Keywords.OVERRIDE
                consumeIf("abstract") -> Keywords.ABSTRACT
                consumeIf("data") -> Keywords.DATA_CLASS
                consumeIf("value") -> Keywords.VALUE
                consumeIf("annotation") -> Keywords.ANNOTATION
                consumeIf("final") -> Keywords.FINAL
                else -> throw IllegalStateException("Unknown keyword ${tokens.toString(i)} at ${tokens.err(i)}")
            }
            setLSType(i - 1, VSCodeType.KEYWORD, 0)
            return
        }

        throw IllegalStateException("Unknown keyword ${tokens.toString(i)} at ${tokens.err(i)}")
    }

    fun readParameterExpressions(): ArrayList<NamedParameter> {
        val params = ArrayList<NamedParameter>()
        while (i < tokens.size) {
            val name = if (tokens.equals(i, TokenType.NAME) &&
                tokens.equals(i + 1, "=")
            ) tokens.toString(i).apply { i += 2 } else null
            val value = readExpression()
            val param = NamedParameter(name, value)
            params.add(param)
            if (LOGGER.isDebugEnabled) LOGGER.debug("read param: $param")
            readComma()
        }
        return params
    }

    override fun readParameterDeclarations(selfType: Type?): List<Parameter> {
        val parameters = ArrayList<Parameter>()
        loop@ while (i < tokens.size) {

            while (consumeIf("@")) {
                annotations.add(readAnnotation())
            }

            while ((tokens.equals(i, TokenType.KEYWORD) || tokens.equals(i, TokenType.NAME)) &&
                !tokens.equals(i + 1, ":")
            ) {
                keywords = keywords or when {
                    consumeIf("final") -> Keywords.FINAL
                    else -> break
                }
                setLSType(i - 1, VSCodeType.KEYWORD, 0)
            }

            val isVal = keywords.hasFlag(Keywords.FINAL)
            check(tokens.equals(i, TokenType.NAME) || tokens.equals(i, TokenType.KEYWORD))

            var type = readTypeNotNull(null, true)
            val isVararg = consumeIf("...")
            if (isVararg) type = ClassType(ArrayType.clazz, listOf(type))

            val origin = origin(i)
            val name = consumeName(VSCodeType.PARAMETER, 0)
            consume(":")

            val initialValue = null

            // println("Found $name: $type = $initialValue at ${resolveOrigin(i)}")

            val keywords = packKeywords()
            val parameter = Parameter(
                parameters.size, !isVal, isVal, isVararg, name, type,
                initialValue, currPackage, origin
            )
            parameter.getOrCreateField(selfType, keywords)
            parameters.add(parameter)

            readComma()
        }
        return parameters
    }

    override fun readBodyOrExpression(label: String?): Expression {
        return if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
            // if just names and -> follow, read a single expression instead
            // if a destructuring and -> follow, read a single expression instead
            var j = i + 1
            //var depth = 0
            arrowSearch@ while (j < tokens.size) {
                when {
                    // tokens.equals(j, TokenType.OPEN_CALL) -> depth++
                    // tokens.equals(j, TokenType.CLOSE_CALL) -> depth--
                    tokens.equals(j, "*") ||
                            tokens.equals(j, "?") ||
                            tokens.equals(j, ".") ||
                            tokens.equals(j, TokenType.COMMA) ||
                            tokens.equals(j, TokenType.NAME) -> {
                    }
                    tokens.equals(j, "->") -> {
                        //if (depth == 0) {
                        return readExprInNewScope(label)
                        //}
                    }
                    else -> break@arrowSearch
                }
                j++
            }

            val scopeName = currPackage.generateName("body", origin(i))
            pushBlock(ScopeType.METHOD_BODY, scopeName) { scope ->
                scope.breakLabel = label
                readMethodBody()
            }
        } else {
            readExprInNewScope(label)
        }
    }

    private fun readExprInNewScope(label: String?): Expression {
        val origin = origin(i)
        val scopeName = currPackage.generateName("expr", origin)
        return pushScope(scopeName, ScopeType.METHOD_BODY) { scope ->
            scope.breakLabel = label
            readExpression()
        }
    }

    private fun readPrefix(): Expression {

        val label =
            if (tokens.equals(i, TokenType.LABEL)) tokens.toString(i++)
            else null

        return when {
            consumeIf("@", VSCodeType.DECORATOR, 0) -> {
                val annotation = readAnnotation()
                AnnotatedExpression(annotation, readPrefix())
            }
            consumeIf("null") -> SpecialValueExpression(SpecialValue.NULL, currPackage, origin(i - 1))
            consumeIf("true") -> SpecialValueExpression(SpecialValue.TRUE, currPackage, origin(i - 1))
            consumeIf("false") -> SpecialValueExpression(SpecialValue.FALSE, currPackage, origin(i - 1))
            consumeIf("super") -> SpecialValueExpression(SpecialValue.SUPER, currPackage, origin(i - 1))
            consumeIf("this") -> ThisExpression(resolveThisLabel(label), currPackage, origin(i - 1))
            tokens.equals(i, TokenType.NUMBER) -> NumberExpression(tokens.toString(i), currPackage, origin(i++))
            tokens.equals(i, TokenType.STRING) -> StringExpression(tokens.toString(i), currPackage, origin(i++))
            consumeIf("!") -> {
                val origin = origin(i - 1)
                val base = readExpression()
                NamedCallExpression(base, "not", currPackage, origin)
            }
            consumeIf("+") -> {
                val origin = origin(i - 1)
                val base = readExpression()
                NamedCallExpression(base, "unaryPlus", currPackage, origin)
            }
            consumeIf("-") -> {
                val origin = origin(i - 1)
                val base = readExpression()
                NamedCallExpression(base, "unaryMinus", currPackage, origin)
            }
            consumeIf("++") -> createPrefixExpression(InplaceModifyType.INCREMENT, origin(i - 1), readExpression())
            consumeIf("--") -> createPrefixExpression(InplaceModifyType.DECREMENT, origin(i - 1), readExpression())
            consumeIf("*") -> {
                ArrayToVarargsStar(readExpression())
            }
            consumeIf("::") -> {
                val origin = origin(i - 1)
                check(tokens.equals(i, TokenType.NAME))
                val name = tokens.toString(i++)
                // :: means a function of the current class
                DoubleColonLambda(currPackage, name, currPackage, origin)
            }

            consumeIf("if") -> readIfBranch()
            consumeIf("else") -> throw IllegalStateException("Standalone 'else' at ${tokens.err(i - 1)}")
            consumeIf("while") -> readWhileLoop(label)
            consumeIf("do") -> readDoWhileLoop(label)
            consumeIf("for") -> readForLoop(label)
            // todo read a switch-case, like in C/C++?
            /*consumeIf("when") -> {
                when {
                    tokens.equals(i, TokenType.OPEN_CALL) -> readWhenWithSubject(label)
                    tokens.equals(i, TokenType.OPEN_BLOCK) -> readWhenWithConditions(label)
                    else -> throw IllegalStateException("Unexpected token after when at ${tokens.err(i)}")
                }
            }*/
            consumeIf("try") -> readTryCatch()
            consumeIf("return") -> readReturn(label)
            consumeIf("throw") -> {
                val origin = origin(i - 1)
                ThrowExpression(readExpression(), currPackage, origin)
            }
            consumeIf("break") -> BreakExpression(resolveBreakLabel(label), currPackage, origin(i - 1))
            consumeIf("continue") -> ContinueExpression(resolveBreakLabel(label), currPackage, origin(i - 1))

            // todo inline-classes are done differently:
            //  new Object(...) { body }
            /*tokens.equals(i, "object") &&
                    (tokens.equals(i + 1, ":") || tokens.equals(i + 1, TokenType.OPEN_BLOCK)) -> {
                readInlineClass()
            }*/

            tokens.equals(i, TokenType.NAME) -> {
                val origin = origin(i)
                val vsCodeType =
                    if (tokens.equals(i + 1, TokenType.OPEN_CALL, TokenType.OPEN_BLOCK)) {
                        VSCodeType.METHOD
                    } else VSCodeType.VARIABLE
                val namePath = consumeName(vsCodeType, 0)
                val typeArgs = readTypeParameters(null)
                if (
                    tokens.equals(i, TokenType.OPEN_CALL) &&
                    tokens.isSameLine(i - 1, i)
                ) {
                    // constructor or function call with type args
                    val start = i
                    val end = tokens.findBlockEnd(i, TokenType.OPEN_CALL, TokenType.CLOSE_CALL)
                    if (LOGGER.isDebugEnabled) LOGGER.debug(
                        "tokens for params: ${
                            (start..end).map { idx ->
                                "${tokens.getType(idx)}(${tokens.toString(idx)})"
                            }
                        }"
                    )
                    val args = pushCall { readParameterExpressions() }
                    val base = nameExpression(namePath, origin, currPackage)
                    CallExpression(base, typeArgs, args, origin + 1)
                } else if (
                // todo validate that we have nothing before us...
                    tokens.equals(i, "::") && tokens.equals(i + 1, "class")) {
                    val i0 = i + 2
                    i-- // skipping over name
                    val type = readTypeNotNull(null, false)
                    check(tokens.equals(i++, "::"))
                    check(tokens.equals(i++, "class"))
                    check(i == i0)
                    GetClassFromTypeExpression(type, currPackage, origin)
                } else {
                    nameExpression(namePath, origin, currPackage)
                }
            }
            tokens.equals(i, TokenType.OPEN_CALL) -> {
                // just something in brackets
                pushCall { readExpression() }
            }
            tokens.equals(i, TokenType.OPEN_BLOCK) ->
                pushBlock(ScopeType.LAMBDA, null) { readLambda(null) }

            else -> {
                tokens.printTokensInBlocks(max(i - 5, 0))
                throw NotImplementedError("Unknown expression part at ${tokens.err(i)}")
            }
        }
    }

    private fun readForLoop(label: String?): Expression {
        TODO("read C-style for loop")
    }

    private fun readReturn(label: String?): ReturnExpression {
        val origin = origin(i - 1)
        if (LOGGER.isDebugEnabled) LOGGER.debug("reading return")
        if (i < tokens.size && tokens.isSameLine(i - 1, i) &&
            !tokens.equals(i, ",", ";")
        ) {
            val value = readExpression()
            if (LOGGER.isDebugEnabled) LOGGER.debug("  with value $value")
            return ReturnExpression(value, label, currPackage, origin)
        } else {
            if (LOGGER.isDebugEnabled) LOGGER.debug("  without value")
            return ReturnExpression(unitInstance, label, currPackage, origin)
        }
    }

    fun <R> pushBlock(scopeType: ScopeType, scopeName: String?, readImpl: (Scope) -> R): R {
        val name = scopeName ?: currPackage.generateName(scopeType.name, origin(i))
        return pushScope(name, scopeType) { childScope ->
            val blockEnd = tokens.findBlockEnd(i++, TokenType.OPEN_BLOCK, TokenType.CLOSE_BLOCK)
            val result = tokens.push(blockEnd) { readImpl(childScope) }
            consume(TokenType.CLOSE_BLOCK)
            result
        }
    }

    fun <R> pushBlock(scope: Scope, readImpl: (Scope) -> R): R {
        return pushScope(scope) {
            val blockEnd = tokens.findBlockEnd(i++, TokenType.OPEN_BLOCK, TokenType.CLOSE_BLOCK)
            val result = tokens.push(blockEnd) { readImpl(scope) }
            consume(TokenType.CLOSE_BLOCK)
            result
        }
    }

    private fun <R> push(endTokenIdx: Int, readImpl: () -> R): R {
        val result = tokens.push(endTokenIdx, readImpl)
        i = endTokenIdx + 1 // skip }
        return result
    }

    private fun nullExpr(scope: Scope, origin: Int): SpecialValueExpression {
        return SpecialValueExpression(SpecialValue.NULL, scope, origin)
    }

    private fun isNotNullCondition(expr: Expression, scope: Scope, origin: Int): Expression {
        val nullExpr = nullExpr(scope, origin)
        return CheckEqualsOp(expr, nullExpr, false, true, scope, origin)
    }

    override fun readExpression(minPrecedence: Int): Expression {
        var expr = readPrefix()
        if (LOGGER.isDebugEnabled) LOGGER.debug("prefix: $expr")

        // main elements
        loop@ while (i < tokens.size) {
            val symbol = when (tokens.getType(i)) {
                TokenType.SYMBOL, TokenType.KEYWORD -> tokens.toString(i)
                TokenType.NAME -> break@loop
                TokenType.APPEND_STRING -> "+"
                else -> {
                    // postfix
                    expr = tryReadPostfix(expr) ?: break@loop
                    continue@loop
                }
            }

            if (LOGGER.isDebugEnabled) LOGGER.debug("symbol $symbol, valid? ${symbol in operators}")

            val op = operators[symbol]
            if (op == null) {
                // postfix
                expr = tryReadPostfix(expr) ?: break@loop
            } else {

                if (op.precedence < minPrecedence) break@loop

                val origin = origin(i)
                i++ // consume operator

                val scope = currPackage
                expr = when (symbol) {
                    "as" -> createCastExpression(
                        expr, scope, origin,
                        readTypeNotNull(null, true)
                    ) { ifFalseScope ->
                        val debugInfoExpr = StringExpression(expr.toString(), ifFalseScope, origin)
                        val debugInfoParam = NamedParameter(null, debugInfoExpr)
                        CallExpression(
                            UnresolvedFieldExpression("throwNPE", shouldBeResolvable, ifFalseScope, origin),
                            emptyList(), listOf(debugInfoParam), origin
                        )
                    }
                    "as?" -> createCastExpression(
                        expr, scope, origin,
                        readTypeNotNull(null, true)
                    ) { scope -> nullExpr(scope, origin) }
                    "is" -> {
                        val type = readTypeNotNull(null, true)
                        IsInstanceOfExpr(expr, type, scope, origin)
                    }
                    "!is" -> {
                        val type = readTypeNotNull(null, true)
                        IsInstanceOfExpr(expr, type, scope, origin).not()
                    }
                    "?:" -> createBranchExpression(
                        expr, scope, origin,
                        { fieldExpr -> isNotNullCondition(fieldExpr, scope, origin) },
                        { fieldExpr, scope -> fieldExpr.clone(scope) }, // not null -> just the field
                        { scope -> pushScope(scope) { readExpression() } },
                    )
                    "?." -> createBranchExpression(
                        expr, scope, origin,
                        { fieldExpr -> isNotNullCondition(fieldExpr, scope, origin) },
                        { fieldExpr, scope ->
                            pushScope(scope) {
                                handleDotOperator(fieldExpr.clone(scope))
                            }
                        },
                        { scope -> nullExpr(scope, origin) },
                    )
                    "." -> handleDotOperator(expr)
                    "&&", "||" -> {
                        val name = currPackage.generateName("shortcut", origin)
                        val right = pushScope(name, ScopeType.METHOD_BODY) { readRHS(op) }
                        if (symbol == "&&") shortcutExpressionI(expr, ShortcutOperator.AND, right, scope, origin)
                        else shortcutExpressionI(expr, ShortcutOperator.OR, right, scope, origin)
                    }
                    "::" -> {
                        if (tokens.equals(i, "class")) {
                            when (expr) {
                                is UnresolvedFieldExpression -> {
                                    val i0 = i + 1
                                    i -= 2 // skipping over :: and name
                                    val type = readTypeNotNull(null, false)
                                    check(tokens.equals(i++, "::"))
                                    check(tokens.equals(i++, "class"))
                                    check(i == i0)
                                    GetClassFromTypeExpression(type, scope, origin)
                                }
                                else -> throw NotImplementedError("Use left side as type ($expr, ${expr.javaClass.simpleName}), then generate ::class")
                            }
                        } else {
                            val rhs = readRHS(op)
                            binaryOp(currPackage, expr, op.symbol, rhs)
                        }
                    }
                    else -> {
                        // println("Reading RHS, symbol: $symbol")
                        val rhs = readRHS(op)
                        binaryOp(currPackage, expr, op.symbol, rhs)
                    }
                }
            }
        }

        return expr
    }

    private fun readRHS(op: Operator): Expression =
        readExpression(if (op.assoc == Assoc.LEFT) op.precedence + 1 else op.precedence)

    private fun handleDotOperator(lhs: Expression): Expression {
        val op = dotOperator
        val rhs = readRHS(op)
        if (rhs is CallExpression && rhs.self is MemberNameExpression) {
            return NamedCallExpression(
                lhs, rhs.self.name, rhs.self.nameAsImport,
                rhs.typeParameters, rhs.valueParameters,
                rhs.scope, rhs.origin
            )
        }
        return binaryOp(currPackage, lhs, op.symbol, rhs)
    }

    private fun tryReadPostfix(expr: Expression): Expression? {
        return when {
            i >= tokens.size -> null
            tokens.equals(i, TokenType.OPEN_CALL) -> {
                val origin = origin(i)
                val params = pushCall { readParameterExpressions() }
                if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
                    pushBlock(ScopeType.LAMBDA, null) { params += NamedParameter(null, readLambda(null)) }
                }
                CallExpression(expr, null, params, origin)
            }
            tokens.equals(i, TokenType.OPEN_ARRAY) -> {
                val origin = origin(i)
                val params = pushArray { readParameterExpressions() }
                if (consumeIf("=")) {
                    val value = NamedParameter(null, readExpression())
                    NamedCallExpression(
                        expr, "set", nameAsImport("set"),
                        null, params + value, expr.scope, origin
                    )
                } else if (tokens.equals(i, TokenType.SYMBOL) && tokens.endsWith(i, '=') &&
                    !(tokens.equals(i, "==", "!=") || tokens.equals(i, "!==", "==="))
                ) {
                    val symbol = tokens.toString(i++)
                    val value = readExpression()
                    val call = NamedCallExpression(
                        expr, "get/set", nameAsImport("get") + nameAsImport("set"),
                        null, params, expr.scope, origin
                    )
                    AssignIfMutableExpr(
                        call, symbol,
                        nameAsImport(plusName(symbol)),
                        nameAsImport(plusAssignName(symbol)),
                        value
                    )
                } else {
                    // array access is a getter
                    NamedCallExpression(
                        expr, "get", nameAsImport("get"),
                        null, params, expr.scope, origin
                    )
                }
            }
            tokens.equals(i, TokenType.OPEN_BLOCK) -> {
                val origin = origin(i)
                val lambda = pushBlock(ScopeType.LAMBDA, null) { readLambda(null) }
                val lambdaParam = NamedParameter(null, lambda)
                CallExpression(expr, null, listOf(lambdaParam), origin)
            }
            consumeIf("++") -> createPostfixExpression(expr, InplaceModifyType.INCREMENT, origin(i - 1))
            consumeIf("--") -> createPostfixExpression(expr, InplaceModifyType.DECREMENT, origin(i - 1))
            else -> null
        }
    }

    private fun readLambda(selfType: SelfType?): Expression {
        val arrow = tokens.findToken(i, "->")
        val variables = if (arrow >= 0) {
            val variables = ArrayList<LambdaVariable>()
            tokens.push(arrow) {
                while (i < tokens.size) {
                    if (tokens.equals(i, TokenType.OPEN_CALL)) {
                        val origin0 = origin(i)
                        val names = ArrayList<LambdaVariable>()
                        pushCall {
                            while (i < tokens.size) {
                                check(tokens.equals(i, TokenType.NAME)) {
                                    "Expected lambda-variable name at ${tokens.err(i)}"
                                }
                                names.add(readLambdaVariable(selfType))
                                readComma()
                            }
                        }
                        val name = "__ld${variables.size}"
                        val field = currPackage.addField( // this is more of a parameter...
                            null, false, isMutable = false, null,
                            name, null, null, Keywords.SYNTHETIC, origin0
                        )
                        val variable = LambdaDestructuring(names, field)
                        field.byParameter = variable
                        variables.add(variable)
                    } else if (tokens.equals(i, TokenType.NAME)) {
                        variables.add(readLambdaVariable(selfType))
                    } else throw NotImplementedError()
                    readComma()
                }
            }
            consume("->")
            variables
        } else null
        val body = readMethodBody()
        check(currPackage.scopeType == ScopeType.LAMBDA)
        return LambdaExpression(variables, currPackage, body)
    }

    private fun readLambdaVariable(selfType: Type?): LambdaVariable {
        val origin = origin(i)
        val name = tokens.toString(i++)
        val type = readTypeOrNull(selfType)
        // to do we neither know type nor initial value :/, both come from the called function/set variable
        val field = currPackage.addField( // this is more of a parameter...
            null, false, isMutable = false, null,
            name, null, null, Keywords.NONE, origin
        )
        val variable = LambdaVariable(type, field)
        field.byParameter = variable
        return variable
    }

    override fun readMethodBody(): ExpressionList {
        val methodScope = currPackage
        val origin = origin(i)
        val result = ArrayList<Expression>()
        if (LOGGER.isDebugEnabled) LOGGER.debug("reading function body[$i], ${tokens.err(i)}")
        if (debug) tokens.printTokensInBlocks(i)
        while (i < tokens.size) {
            val oldSize = result.size
            val oldNumFields = currPackage.fields.size

            when {
                tokens.equals(i, TokenType.CLOSE_BLOCK) ->
                    throw IllegalStateException("} in the middle at ${tokens.err(i)}")
                consumeIf(";") -> {} // skip
                consumeIf("@") -> annotations.add(readAnnotation())

                // todo we must skip some stuff, before we know whether we read a field declaration,
                //  or just some assignment, or call
                // todo methods inside methods aren't supported anyway

                consumeIf("val") || consumeIf("var") -> {
                    // immediately split scope to avoid duplicate fields on a context
                    val isMutable = tokens.equals(i - 1, "var")
                    val oldScope = currPackage
                    val subName = oldScope.generateName("split")
                    val newScope = oldScope.getOrPut(subName, ScopeType.METHOD_BODY)
                    // field shall be declared in newScope, but expr shall be in old scope...
                    val declaration = readFieldInMethod(isMutable, newScope)
                    pushScope(newScope) {
                        val remainder = readMethodBody()
                        (remainder.list as ArrayList<Expression>).add(0, declaration)
                        result.add(remainder)
                    }
                    break
                }
                else -> {
                    result.add(readExpression())
                    if (LOGGER.isDebugEnabled) LOGGER.debug("block += ${result.last()}")
                }
            }

            // todo check whether this works correctly
            // if expression contains assignment of any kind, or a check-call
            //  we must create a new sub-scope,
            //  because the types of our fields may have changed
            if (shouldSplitIntoSubScope(oldSize, oldNumFields, result)) {
                splitIntoSubScope(oldNumFields, result)
            }
        }
        val code = ExpressionList(result, methodScope, origin)
        // methodScope.code.add(code)
        currPackage = methodScope // restore scope
        return code
    }

    private fun readFieldInMethod(isMutable: Boolean, fieldScope: Scope): Expression {

        val i0 = i
        val origin = origin(i0)
        var afterName = i
        while (afterName < tokens.size) {
            if (tokens.equals(afterName, ":", "=")) break
            else afterName++
        }

        val selfType = if (afterName > i + 1) {
            check(tokens.equals(afterName - 2, ".")) {
                "Expected dot to separate type and name for field at ${tokens.err(i0)}"
            }
            val type = tokens.push(afterName - 2) {
                readType(null, true)
            }
            consume(".")
            check(i == afterName - 1) { "Unused tokens at ${tokens.err(i)}" }
            type
        } else null

        val name = consumeName(VSCodeType.VARIABLE, VSCodeModifier.DECLARATION.flag)
        val keywords = packKeywords()

        if (LOGGER.isDebugEnabled) LOGGER.debug("reading var/val $name")

        val type = readTypeOrNull(null)
        val initialValue = if (consumeIf("=")) readExpression() else null
        check(type != null || initialValue != null) { "Field at ${tokens.err(i0)} either needs a type or a value" }

        // define variable in the scope
        val field = fieldScope.addField(
            selfType, selfType != null, isMutable = isMutable, null,
            name, type, initialValue, keywords, origin
        )

        return createDeclarationExpression(fieldScope, initialValue, field)
    }
}