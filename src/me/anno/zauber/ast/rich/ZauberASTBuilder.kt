package me.anno.zauber.ast.rich

import me.anno.langserver.VSCodeModifier
import me.anno.langserver.VSCodeType
import me.anno.zauber.ZauberLanguage
import me.anno.zauber.ast.KeywordSet
import me.anno.zauber.ast.rich.DataClassGenerator.finishDataClass
import me.anno.zauber.ast.rich.FieldGetterSetter.finishLastField
import me.anno.zauber.ast.rich.FieldGetterSetter.readGetter
import me.anno.zauber.ast.rich.FieldGetterSetter.readSetter
import me.anno.zauber.ast.rich.Keywords.hasFlag
import me.anno.zauber.ast.rich.ScopeSplit.shouldSplitIntoSubScope
import me.anno.zauber.ast.rich.ScopeSplit.splitIntoSubScope
import me.anno.zauber.ast.rich.ZauberASTClassScanner.Companion.resolveTypeAliases
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
import me.anno.zauber.typeresolution.TypeResolution.getSelfType
import me.anno.zauber.types.BooleanUtils.not
import me.anno.zauber.types.Import
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.AnyType
import me.anno.zauber.types.Types.ArrayType
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.ListType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.Types.ThrowableType
import me.anno.zauber.types.Types.UnitType
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.NullType.typeOrNull
import me.anno.zauber.types.impl.SelfType
import kotlin.math.max
import kotlin.math.min

// I want macros... how could we implement them? learn about Rust macros
//  -> we get tokens as attributes in a specific pattern,
//  and after resolving the pattern, we can copy-paste these pattern variables as we please
//  -> we should be able to implement when() and for() using these
//  -> do we even need macros when we have a good language? :)

class ZauberASTBuilder(
    tokens: TokenList, root: Scope,
    val language: ZauberLanguage = ZauberLanguage.ZAUBER
) : ZauberASTBuilderBase(tokens, root, false) {

    companion object {

        private val LOGGER = LogManager.getLogger(ZauberASTBuilder::class)

        val fileLevelKeywords = listOf(
            "enum", "private", "protected", "fun", "class", "data", "value",
            "companion", "object", "constructor", "inline",
            "override", "abstract", "open", "final", "operator",
            "const", "lateinit", "annotation", "internal", "inner", "sealed",
            "infix", "external"
        )

        val supportedInfixFunctions = listOf(
            // these shall only be supported for legacy reasons: I dislike that their order of precedence isn't clear
            "shl", "shr", "ushr", "and", "or", "xor",

            // I like these:
            "in", "to", "step", "until", "downTo",
            "is", "!is", "as", "as?"
        )

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
        val vsCodeType = when (scopeType) {
            ScopeType.INTERFACE -> VSCodeType.INTERFACE
            ScopeType.ENUM_CLASS -> VSCodeType.ENUM
            else -> VSCodeType.CLASS
        }

        val name = consumeName(vsCodeType, VSCodeModifier.DECLARATION.flag)

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

    private fun readObject(scopeType: ScopeType) {
        val origin = origin(i - 1)
        val isCompanionObject = scopeType == ScopeType.COMPANION_OBJECT
        val name = if (tokens.equals(i, TokenType.NAME, TokenType.KEYWORD)) {
            consumeName(VSCodeType.CLASS, VSCodeModifier.DECLARATION.flag)
        } else if (isCompanionObject) {
            "Companion"
        } else throw IllegalStateException("Missing object name at ${tokens.err(i)}")

        if (tokens.equals(i, "(", "<")) {
            throw IllegalStateException("Objects only exist once, so they cannot have constructor parameters.")
        }

        val keywords = packKeywords()
        // println("Read object '$name' with keywords: $keywords in scope $currPackage")

        val classScope = currPackage.getOrPut(name, tokens.fileName, scopeType)
        readSuperCalls(classScope)

        val primaryConstructor = classScope.getOrCreatePrimConstructorScope()
        primaryConstructor.selfAsConstructor = Constructor(
            emptyList(), primaryConstructor,
            null, ExpressionList(ArrayList(), primaryConstructor, origin), keywords, origin
        )

        readClassBody(name, keywords, scopeType)

        classScope.hasTypeParameters = true // no type-params are supported
        if (classScope.objectField == null) classScope.objectField = classScope.addField(
            null, false, isMutable = false, null, classScope.name,
            ClassType(classScope, emptyList()),
            /* todo should we set initialValue? */ null, Keywords.NONE, origin
        )

        if (isCompanionObject) {
            check(currPackage.companionObject != null) {
                "Expected class to have companion object"
            }
        }
    }

    private fun readSuperCalls(classScope: Scope) {
        if (consumeIf(":")) {
            var endIndex = findEndOfSuperCalls(i)
            if (endIndex < 0) endIndex = tokens.size
            push(endIndex) {
                while (i < tokens.size) {
                    classScope.superCalls.add(readSuperCall(classScope.typeWithoutArgs))
                    readComma()
                }
            }
            i = endIndex // index of {
        }
        val addAnyIfEmpty = classScope != AnyType.clazz
        if (addAnyIfEmpty && classScope.superCalls.isEmpty()) {
            classScope.superCalls.add(SuperCall(AnyType, emptyList(), null))
        }
    }

    /**
     * looks for new keywords, or reading depth < 0 by brackets
     * */
    private fun findEndOfSuperCalls(i0: Int): Int {
        var depth = 0
        for (i in i0 until tokens.size) {
            if (depth == 0) {
                if (tokens.equals(i, TokenType.OPEN_BLOCK) ||
                    tokens.equals(i, TokenType.OPEN_ARRAY) ||
                    tokens.equals(i, TokenType.KEYWORD) ||
                    fileLevelKeywords.any { tokens.equals(i, it) }
                ) return i
            }
            when {
                tokens.equals(i, TokenType.OPEN_BLOCK) ||
                        tokens.equals(i, TokenType.OPEN_ARRAY) ||
                        tokens.equals(i, TokenType.OPEN_CALL) -> depth++
                tokens.equals(i, TokenType.CLOSE_BLOCK) ||
                        tokens.equals(i, TokenType.CLOSE_ARRAY) ||
                        tokens.equals(i, TokenType.CLOSE_CALL) -> depth--
            }
        }
        return -1
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
                    readValueParameters()
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

    var lastField: Field? = null

    private fun readFieldInClass(isMutable: Boolean) {
        val origin = origin(i - 1)

        val classScope = currPackage
        val typeParameters = readTypeParameterDeclarations(classScope)
        val selfType0 = readFieldOrMethodSelfType(typeParameters, classScope)
        var selfType = selfType0 ?: getSelfType(classScope)
        if (selfType != null) selfType = resolveTypeAliases(selfType)

        check(tokens.equals(i, TokenType.NAME))
        val name = consumeName(VSCodeType.PROPERTY, VSCodeModifier.DECLARATION.flag)
        val keywords = packKeywords()

        val type = readTypeOrNull(selfType)

        val constructorScope = classScope.getOrCreatePrimConstructorScope()
        val initialValue = pushScope(constructorScope) {
            // println("Fields in primary constructor for $primScope: ${primScope.fields}")
            if (tokens.equals(i, "=")) {
                i++
                // todo find reasonable end index, e.g. fun, class, private, object, and limit to that
                readExpression()
            } else if (tokens.equals(i, "by")) {
                i++
                // todo find reasonable end index, e.g. fun, class, private, object, and limit to that
                DelegateExpression(readExpression())
            } else null
        }

        val field = currPackage.addField(
            selfType, selfType0 != null, isMutable = isMutable, null,
            name, type, initialValue, keywords, origin
        )

        field.typeParameters = typeParameters
        if (initialValue != null) {
            val fieldExpr = FieldExpression(field, classScope, origin)
            check(constructorScope.selfAsConstructor != null) {
                "$classScope needs primary constructor"
            }
            check(constructorScope.selfAsConstructor?.body != null) {
                "$classScope needs primary constructor with body"
            }
            constructorScope.code.add(AssignmentExpression(fieldExpr, initialValue))
        }

        if (LOGGER.isDebugEnabled) LOGGER.debug("read field $name: $type = $initialValue")

        finishLastField()
        lastField = field
        popGenericParams()
    }

    private fun skipTypeParametersToFindFunctionNameAndScope(origin: Int): Scope {
        var j = i
        if (tokens.equals(j, "<")) {
            j = tokens.findBlockEnd(j, "<", ">") + 1
        }
        check(tokens.equals(j, TokenType.NAME))
        val methodName = tokens.toString(j)
        val uniqueName = currPackage.generateName("fun:$methodName", origin)
        return currPackage.getOrPut(uniqueName, tokens.fileName, ScopeType.METHOD)
    }

    private fun readFieldOrMethodSelfType(typeParameters: List<Parameter>, functionScope: Scope): Type? {
        if (tokens.equals(i + 1, ".") ||
            tokens.equals(i + 1, "<") ||
            tokens.equals(i + 1, "?.")
        ) {
            if (tokens.equals(i + 1, ".")) {
                // avoid packing both type and function name into one
                val name = tokens.toString(i++)
                val type = currPackage.resolveType(
                    name, typeParameters,
                    functionScope, this,
                )
                check(tokens.equals(i++, "."))
                return type
            } else {
                var type = readType(null, false)
                    ?: return null
                if (tokens.equals(i, "?.")) {
                    type = typeOrNull(type)
                    i++
                } else {
                    check(tokens.equals(i++, "."))
                }
                return type
            }
        } else return null
    }

    private fun readWhereConditions(): List<TypeCondition> {
        return if (consumeIf("where")) {
            val conditions = ArrayList<TypeCondition>()
            while (true) {

                check(tokens.equals(i, TokenType.NAME))
                check(tokens.equals(i + 1, ":"))

                val name = tokens.toString(i++)
                consume(TokenType.COMMA)
                val type = readTypeNotNull(null, true)
                conditions.add(TypeCondition(name, type))

                if (tokens.equals(i, ",") &&
                    tokens.equals(i + 1, TokenType.NAME) &&
                    tokens.equals(i + 2, ":")
                ) {
                    // skip comma and continue reading conditions
                    consume(TokenType.COMMA)
                } else {
                    // done
                    break
                }
            }
            conditions
        } else emptyList()
    }

    private fun readMethod(): Method {
        val origin = origin(i - 1) // on 'fun'

        val keywords = packKeywords()

        // parse optional <T, U>
        val classScopeIfInClass = if (currPackage.isClassType()) currPackage else null
        val methodScope = skipTypeParametersToFindFunctionNameAndScope(origin)
        val typeParameters = readTypeParameterDeclarations(methodScope)

        check(tokens.equals(i, TokenType.NAME))
        val selfType0 = readFieldOrMethodSelfType(typeParameters, methodScope)
        val selfType = selfType0 ?: getSelfType(methodScope)

        check(tokens.equals(i, TokenType.NAME))
        val name = consumeName(VSCodeType.METHOD, VSCodeModifier.DECLARATION.flag)

        if (LOGGER.isDebugEnabled) LOGGER.debug("fun <$typeParameters> ${if (selfType != null) "$selfType." else ""}$name(...")

        // parse parameters (...)
        check(tokens.equals(i, TokenType.OPEN_CALL)) {
            "Expected () for method call $selfType.$name, but found ${tokens.err(i)}"
        }

        lateinit var parameters: List<Parameter>
        pushScope(methodScope) {
            parameters = pushCall {
                val selfType = classScopeIfInClass?.typeWithoutArgs
                readParameterDeclarations(selfType)
            }
        }

        // optional return type
        var returnType =
            if (!tokens.equals(i, "=", ":")) UnitType // todo if there is a where, we first need to skip it
            else readTypeOrNull(selfType)
        val extraConditions = readWhereConditions()

        val method = Method(
            selfType, selfType0 != null, name, typeParameters, parameters, methodScope,
            returnType, extraConditions, null, keywords, origin
        )
        methodScope.selfAsMethod = method

        // body (or just = expression)
        method.body = pushScope(methodScope) {
            if (tokens.equals(i, "=")) {
                val origin = origin(i++) // skip =
                ReturnExpression(readExpression(), null, methodScope, origin)
            } else if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
                if (returnType == null) returnType = UnitType
                pushBlock(methodScope) { readMethodBody() }
            } else {
                if (returnType == null) returnType = UnitType
                null
            }
        }

        popGenericParams()
        return method
    }

    private fun readConstructor(): Constructor {
        val origin = origin(i - 1) // on 'constructor'
        val keywords = packKeywords()

        if (LOGGER.isDebugEnabled) LOGGER.debug("constructor(...")

        // parse parameters (...)
        check(tokens.equals(i, TokenType.OPEN_CALL))
        lateinit var parameters: List<Parameter>
        val classScope = currPackage
        val isEnumClass = classScope.scopeType == ScopeType.ENUM_CLASS

        val name = classScope.generateName("constructor", origin)
        val constructorScope = pushScope(name, ScopeType.CONSTRUCTOR) { constructorScope ->
            val selfType = ClassType(classScope, null)
            parameters = pushCall { readParameterDeclarations(selfType) }
            if (isEnumClass) {
                parameters = listOf(
                    Parameter(0, "ordinal", IntType, constructorScope, origin),
                    Parameter(1, "name", StringType, constructorScope, origin)
                ) + parameters.map { it.shift(2) }
            }
            constructorScope
        }

        // optional return type
        val superCall = if (tokens.equals(i, ":")) {
            i++
            check(tokens.equals(i, "this") || tokens.equals(i, "super"))
            val origin = origin(i)
            val target = if (tokens.equals(i++, "super"))
                InnerSuperCallTarget.SUPER else InnerSuperCallTarget.THIS
            // val typeParams = readTypeParams(null) // <- not supported
            var parameters = if (tokens.equals(i, TokenType.OPEN_CALL)) {
                readValueParameters()
            } else emptyList()
            if (isEnumClass) {
                parameters = listOf(
                    NamedParameter(
                        "ordinal",
                        UnresolvedFieldExpression("ordinal", shouldBeResolvable, constructorScope, origin)
                    ),
                    NamedParameter(
                        "name",
                        UnresolvedFieldExpression("name", shouldBeResolvable, constructorScope, origin)
                    )
                ) + parameters
            }
            InnerSuperCall(target, parameters, origin)
        } else null

        // body (or just = expression)
        val body = pushScope(constructorScope) {
            if (consumeIf("=")) {
                readExpression()
            } else if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
                pushBlock(ScopeType.CONSTRUCTOR, null) { readMethodBody() }
            } else null
        }

        val constructor = Constructor(
            parameters, constructorScope,
            superCall, body, keywords, origin
        )
        constructorScope.selfAsConstructor = constructor
        return constructor
    }

    private fun applyImport(import: Import) {
        imports.add(import)
        if (import.allChildren) {
            for (child in import.path.children) {
                currPackage.imports += Import2(child.name, child, false)
            }
        } else {
            currPackage.imports += Import2(import.name, import.path, true)
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

                consumeIf("inner") -> {
                    consume("class")
                    readClass(ScopeType.INNER_CLASS)
                }
                consumeIf("enum") -> {
                    consume("class")
                    readClass(ScopeType.ENUM_CLASS)
                }
                consumeIf("class") -> readClass(ScopeType.NORMAL_CLASS)

                consumeIf("companion") -> {
                    consume("object")
                    readObject(ScopeType.COMPANION_OBJECT)
                }
                consumeIf("object") -> readObject(ScopeType.OBJECT)
                consumeIf("fun") -> {
                    if (consumeIf("interface")) {
                        keywords = keywords or Keywords.FUN_INTERFACE
                        readInterface()
                    } else {
                        readMethod()
                    }
                }
                consumeIf("interface") -> readInterface()
                consumeIf("constructor") -> readConstructor()
                consumeIf("typealias") -> readTypeAlias()
                consumeIf("var") -> readFieldInClass(true)
                consumeIf("val") -> readFieldInClass(false)
                consumeIf("init") -> {
                    check(tokens.equals(i, TokenType.OPEN_BLOCK))
                    pushBlock(currPackage.getOrCreatePrimConstructorScope()) {
                        readMethodBody()
                    }
                }

                consumeIf("get") -> readGetter()
                consumeIf("set") -> readSetter()

                consumeIf("@") -> annotations.add(readAnnotation())

                tokens.equals(i, TokenType.NAME) || tokens.equals(i, TokenType.KEYWORD) ->
                    collectKeywords()

                consumeIf(";") -> {}// just skip it

                else -> throw NotImplementedError("Unknown token at ${tokens.err(i)}")
            }
        }
        finishLastField()
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
            readValueParameters()
        } else emptyList()
        return Annotation(path, params)
    }

    fun collectKeywords() {
        if (!tokens.equals(i, TokenType.STRING)) {
            keywords = keywords or when {
                consumeIf("public") -> Keywords.PUBLIC
                consumeIf("private") -> Keywords.PRIVATE
                consumeIf("external") -> Keywords.EXTERNAL
                consumeIf("open") -> Keywords.OPEN
                consumeIf("override") -> Keywords.OVERRIDE
                consumeIf("abstract") -> Keywords.ABSTRACT
                consumeIf("operator") -> Keywords.OPERATOR
                consumeIf("inline") -> Keywords.INLINE
                consumeIf("infix") -> Keywords.INFIX
                consumeIf("data") -> Keywords.DATA_CLASS
                consumeIf("value") -> Keywords.VALUE
                consumeIf("annotation") -> Keywords.ANNOTATION
                consumeIf("sealed") -> Keywords.SEALED
                consumeIf("const") -> Keywords.CONSTEXPR
                consumeIf("final") -> Keywords.FINAL
                consumeIf("lateinit") -> Keywords.LATEINIT
                else -> throw IllegalStateException("Unknown keyword ${tokens.toString(i)} at ${tokens.err(i)}")
            }
            setLSType(i - 1, VSCodeType.KEYWORD, 0)
            return
        }

        throw IllegalStateException("Unknown keyword ${tokens.toString(i)} at ${tokens.err(i)}")
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
                    consumeIf("private") -> Keywords.PRIVATE
                    consumeIf("public") -> Keywords.PUBLIC
                    consumeIf("protected") -> Keywords.PROTECTED
                    consumeIf("override") -> Keywords.OVERRIDE
                    consumeIf("open") -> Keywords.OPEN
                    consumeIf("value") -> Keywords.VALUE
                    consumeIf("crossinline") -> Keywords.CROSS_INLINE
                    else -> break
                }
                setLSType(i - 1, VSCodeType.KEYWORD, 0)
            }

            val isVararg = consumeIf("vararg", VSCodeType.KEYWORD, 0)
            val isVar = consumeIf("var", VSCodeType.KEYWORD, 0)
            val isVal = consumeIf("val", VSCodeType.KEYWORD, 0)

            val origin = origin(i)
            val name = consumeName(VSCodeType.PARAMETER, 0)
            consume(":")

            var type = readTypeNotNull(null, true) // <-- handles generics now
            if (isVararg) type = ClassType(ArrayType.clazz, listOf(type))

            val initialValue = if (consumeIf("=")) readExpression() else null

            // println("Found $name: $type = $initialValue at ${resolveOrigin(i)}")

            val keywords = packKeywords()
            val parameter = Parameter(
                parameters.size, isVar, isVal, isVararg, name, type,
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
            var depth = 0
            arrowSearch@ while (j < tokens.size) {
                when {
                    tokens.equals(j, TokenType.OPEN_CALL) -> depth++
                    tokens.equals(j, TokenType.CLOSE_CALL) -> depth--
                    tokens.equals(j, "*") ||
                            tokens.equals(j, "?") ||
                            tokens.equals(j, ".") ||
                            tokens.equals(j, TokenType.COMMA) ||
                            tokens.equals(j, TokenType.NAME) -> {
                    }
                    tokens.equals(j, "->") -> {
                        if (depth == 0) {
                            return readExprInNewScope(label)
                        }
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
            consumeIf("when") -> {
                when {
                    tokens.equals(i, TokenType.OPEN_CALL) -> readWhenWithSubject(label)
                    tokens.equals(i, TokenType.OPEN_BLOCK) -> readWhenWithConditions(label)
                    else -> throw IllegalStateException("Unexpected token after when at ${tokens.err(i)}")
                }
            }
            consumeIf("try") -> readTryCatch()
            consumeIf("return") -> readReturn(label)
            consumeIf("throw") -> {
                val origin = origin(i - 1)
                ThrowExpression(readExpression(), currPackage, origin)
            }
            consumeIf("yield", VSCodeType.KEYWORD, 0) -> {
                val origin = origin(i - 1)
                YieldExpression(readExpression(), currPackage, origin)
            }
            consumeIf("async", VSCodeType.KEYWORD, 0) -> {
                val origin = origin(i - 1)
                AsyncExpression(readExpression(), currPackage, origin)
            }
            consumeIf("break") -> BreakExpression(resolveBreakLabel(label), currPackage, origin(i - 1))
            consumeIf("continue") -> ContinueExpression(resolveBreakLabel(label), currPackage, origin(i - 1))

            tokens.equals(i, "object") &&
                    (tokens.equals(i + 1, ":") || tokens.equals(i + 1, TokenType.OPEN_BLOCK)) -> {
                readInlineClass()
            }

            tokens.equals(i, TokenType.NAME, TokenType.KEYWORD) -> {
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
                    val args = readValueParameters()
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

    private fun getNextOuterTypeParams(): List<Parameter> {
        var scope: Scope? = currPackage
        while (scope != null) {
            val scopeType = scope.scopeType
            if (scope.hasTypeParameters && scopeType != null &&
                (scopeType.isMethodType() || scopeType.isClassType())
            ) {
                return scope.typeParameters
            }

            scope = scope.parentIfSameFile
        }
        return emptyList()
    }

    private fun readInlineClass(): Expression {
        val origin = origin(i)
        consume("object")

        val name = currPackage.generateName("inline", origin)
        val classScope = currPackage.getOrPut(name, tokens.fileName, ScopeType.INLINE_CLASS)
        classScope.typeParameters = getNextOuterTypeParams()
        classScope.hasTypeParameters = true

        readSuperCalls(classScope)

        // println("Inline class has the following type-params: ${classScope.typeParameters}")

        readClassBody(name, Keywords.NONE, ScopeType.INLINE_CLASS)
        return ConstructorExpression(
            classScope, emptyList(), emptyList(),
            null, currPackage, origin
        )
    }

    private fun readSuperCall(selfType: Type): SuperCall {
        val i0 = i
        val type = readType(selfType, true) as? ClassType
            ?: throw IllegalStateException("SuperType must be a ClassType, at ${tokens.err(i0)}")

        val valueParams = if (tokens.equals(i, TokenType.OPEN_CALL)) {
            readValueParameters()
        } else null

        val delegate = if (consumeIf("by")) readExpression() else null
        return SuperCall(type, valueParams, delegate)
    }

    private fun readForLoop(label: String?): Expression {
        lateinit var iterable: Expression
        if (tokens.equals(i + 1, TokenType.OPEN_CALL)) {
            // destructuring expression
            lateinit var names: List<FieldDeclaration>
            pushCall {
                check(tokens.equals(i, TokenType.OPEN_CALL))
                names = readDestructuringFields()
                consume("in")
                iterable = readExpression()
                check(i == tokens.size)
            }
            val body = readBodyOrExpression(label ?: "")
            return destructuringForLoop(currPackage, names, iterable, body, label)
        } else {
            lateinit var name: String
            var variableType: Type? = null
            val origin = origin(i)
            pushCall {
                name = consumeName(VSCodeType.VARIABLE, VSCodeModifier.DECLARATION.flag)
                variableType = readTypeOrNull(null)
                consume("in")
                iterable = readExpression()
                check(i == tokens.size)
            }
            val body = readBodyOrExpression(label ?: "")
            val pseudoInitial = iterableToNextExpr(iterable)
            val variableField = body.scope.addField(
                null, false, isMutable = true, false,
                name, variableType, pseudoInitial, Keywords.NONE, origin
            )
            return forLoop(variableField, iterable, body, label)
        }
    }

    private fun readWhenWithSubject(label: String?): Expression {
        val subject = pushCall {
            when {
                consumeIf("val") -> readFieldInMethod(false, isLateinit = false, currPackage)
                consumeIf("var") -> readFieldInMethod(true, isLateinit = false, currPackage)
                else -> readExpression()
            }
        }
        check(tokens.equals(i, TokenType.OPEN_BLOCK))
        val cases = ArrayList<SubjectWhenCase>()
        val childScope = pushBlock(ScopeType.WHEN_CASES, null) { childScope ->
            while (i < tokens.size) {
                if (cases.isNotEmpty()) pushHelperScope()
                val nextArrow = findNextArrow(i)
                check(nextArrow > i) {
                    "Expected nextArrow for whenCases starting at ${tokens.err(i)}, but only found $nextArrow"
                }
                val conditions = readSubjectConditions(nextArrow)
                val body = readBodyOrExpression(label)
                if (false) {
                    LOGGER.info("next case:")
                    LOGGER.info("  condition-scope: ${currPackage.pathStr}")
                    LOGGER.info("  body: $body")
                }
                cases.add(SubjectWhenCase(conditions, currPackage, body))
                consumeIf(";")
            }
            childScope
        }
        return whenSubjectToIfElseChain(childScope, subject, cases)
    }

    private fun readSubjectConditions(nextArrow: Int): List<SubjectCondition>? {
        val conditions = ArrayList<SubjectCondition>()
        if (LOGGER.isDebugEnabled) LOGGER.debug("reading conditions, ${tokens.toString(i, nextArrow)}")
        var hadElse = false
        push(nextArrow) {
            while (i < tokens.size) {
                if (LOGGER.isDebugEnabled) LOGGER.debug("  reading condition $nextArrow,$i,${tokens.err(i)}")
                when {
                    consumeIf("else") -> {
                        hadElse = true
                        return@push null
                    }
                    tokens.equals(i, "in") || tokens.equals(i, "!in") -> {
                        setLSType(i, VSCodeType.OPERATOR, 0)
                        val symbol =
                            if (tokens.toString(i++) == "in") SubjectConditionType.CONTAINS
                            else SubjectConditionType.NOT_CONTAINS
                        val value = readExpression()
                        val extra = if (consumeIf("if")) readExpression() else null
                        conditions.add(SubjectCondition(value, null, symbol, extra))
                    }
                    tokens.equals(i, "is") || tokens.equals(i, "!is") -> {
                        setLSType(i, VSCodeType.OPERATOR, 0)
                        val symbol =
                            if (tokens.toString(i++) == "is") SubjectConditionType.INSTANCEOF
                            else SubjectConditionType.NOT_INSTANCEOF
                        val type = readType(null, false)
                        val extra = if (consumeIf("if")) readExpression() else null
                        conditions.add(SubjectCondition(null, type, symbol, extra))
                    }
                    else -> {
                        val value = readExpression()
                        val extra = if (consumeIf("if")) readExpression() else null
                        conditions.add(SubjectCondition(value, null, SubjectConditionType.EQUALS, extra))
                    }
                }
                if (LOGGER.isDebugEnabled) LOGGER.debug("  read condition '${conditions.last()}'")
                readComma()
            }
        }
        if (!hadElse && conditions.isEmpty()) {
            throw IllegalStateException("Missing conditions at ${tokens.err(i)}")
        }
        if (hadElse) return null
        return conditions
    }

    private fun pushHelperScope() {
        // push a helper scope for if/else differentiation...
        val type = ScopeType.WHEN_ELSE
        currPackage = currPackage.generate(type.name, type)
    }

    private fun findNextArrow(i0: Int): Int {
        var depth = 0
        var j = i0
        while (j < tokens.size) {
            when {
                tokens.equals(j, "is") ||
                        tokens.equals(j, "!is") ||
                        tokens.equals(j, "as") ||
                        tokens.equals(j, "as?") -> {
                    val originalI = i
                    i = j + 1 // after is/!is/as/as?
                    val type = readType(null, false)
                    if (LOGGER.isDebugEnabled) LOGGER.debug("skipping over type '$type'")
                    j = i - 1 // continue after the type; -1, because will be incremented immediately after
                    i = originalI
                }
                depth == 0 && tokens.equals(j, "->") -> {
                    if (LOGGER.isDebugEnabled) LOGGER.debug("found arrow at ${tokens.err(j)}")
                    return j
                }
                tokens.equals(j, TokenType.OPEN_BLOCK) ||
                        tokens.equals(j, TokenType.OPEN_ARRAY) ||
                        tokens.equals(j, TokenType.OPEN_CALL) -> depth++
                tokens.equals(j, TokenType.CLOSE_BLOCK) ||
                        tokens.equals(j, TokenType.CLOSE_ARRAY) ||
                        tokens.equals(j, TokenType.CLOSE_CALL) -> depth--
            }
            j++
        }
        return -1
    }

    private fun readWhenWithConditions(label: String?): Expression {
        val origin = origin(i)
        val cases = ArrayList<WhenCase>()
        pushBlock(ScopeType.WHEN_CASES, null) {
            while (i < tokens.size) {
                if (cases.isNotEmpty()) pushHelperScope()

                val nextArrow = findNextArrow(i)
                check(nextArrow > i) {
                    tokens.printTokensInBlocks(i)
                    "Missing arrow at ${tokens.err(i)} ($nextArrow vs $i)"
                }

                val condition = push(nextArrow) {
                    if (tokens.equals(i, "else")) null
                    else readExpression()
                }

                val body = readBodyOrExpression(label)
                cases.add(WhenCase(condition, body))
            }
        }
        return whenBranchToIfElseChain(cases, currPackage, origin)
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
            var isInfix = false
            val symbol = when (tokens.getType(i)) {
                TokenType.SYMBOL, TokenType.KEYWORD -> tokens.toString(i)
                TokenType.NAME -> {
                    isInfix = true
                    val infix = supportedInfixFunctions.firstOrNull { infix -> tokens.equals(i, infix) }
                    // infix must be on the same line
                    if (infix == null || !tokens.isSameLine(i - 1, i)) break@loop
                    infix
                }
                TokenType.APPEND_STRING -> "+"
                else -> {
                    // postfix
                    expr = tryReadPostfix(expr) ?: break@loop
                    continue@loop
                }
            }
            when (symbol) {
                "in", "!in", "is", "!is", "+", "-" -> {
                    // these must be on the same line
                    if (!tokens.isSameLine(i - 1, i)) {
                        break@loop
                    }
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
                        if (isInfix) {
                            val param = NamedParameter(null, rhs)
                            NamedCallExpression(
                                expr, op.symbol, nameAsImport(op.symbol), null,
                                listOf(param), expr.scope, origin
                            )
                        } else {
                            binaryOp(currPackage, expr, op.symbol, rhs)
                        }
                    }
                }
            }
        }

        return expr
    }

    private fun tryReadPostfix(expr: Expression): Expression? {
        return when {
            i >= tokens.size || !tokens.isSameLine(i - 1, i) -> null
            tokens.equals(i, TokenType.OPEN_CALL) -> {
                val origin = origin(i)
                val params = readValueParameters()
                if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
                    pushBlock(ScopeType.LAMBDA, null) { params += NamedParameter(null, readLambda(null)) }
                }
                CallExpression(expr, null, params, origin)
            }
            tokens.equals(i, TokenType.OPEN_ARRAY) -> {
                val origin = origin(i)
                val params = pushArray { readValueParametersImpl() }
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
            tokens.equals(i, "!!") -> {
                val origin = origin(i++)
                val scope = currPackage
                createBranchExpression(
                    expr, scope, origin,
                    { fieldExpr -> isNotNullCondition(fieldExpr, scope, origin) },
                    { fieldExpr, ifTrueScope ->
                        fieldExpr.clone(ifTrueScope)
                    }, { scope ->
                        val debugInfoExpr = StringExpression(expr.toString(), scope, origin)
                        CallExpression(
                            UnresolvedFieldExpression("throwNPE", shouldBeResolvable, scope, origin), emptyList(),
                            listOf(NamedParameter(null, debugInfoExpr)), origin
                        )
                    }
                )
            }
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
        if (LOGGER.isDebugEnabled) LOGGER.debug("reading method body[$i], ${tokens.err(i)}")
        if (debug) tokens.printTokensInBlocks(i)
        while (i < tokens.size) {
            val oldSize = result.size
            val oldNumFields = currPackage.fields.size

            fun readDeclarationImpl(isMutable: Boolean, isLateinit: Boolean) {
                val oldScope = currPackage
                val subName = oldScope.generateName("split")
                val newScope = oldScope.getOrPut(subName, ScopeType.METHOD_BODY)
                // field shall be declared in newScope, but expr shall be in old scope...
                val declaration = readFieldInMethod(isMutable, isLateinit, newScope)
                pushScope(newScope) {
                    val remainder = readMethodBody()
                    (remainder.list as ArrayList<Expression>).add(0, declaration)
                    result.add(remainder)
                }
            }

            when {
                tokens.equals(i, TokenType.CLOSE_BLOCK) ->
                    throw IllegalStateException("} in the middle at ${tokens.err(i)}")
                consumeIf(";") -> {} // skip
                consumeIf("@") -> annotations.add(readAnnotation())
                consumeIf("lateinit") -> {
                    consume("var")
                    readDeclarationImpl(isMutable = true, isLateinit = true)
                    break
                }
                consumeIf("val") || consumeIf("var") -> {
                    // immediately split scope to avoid duplicate fields on a context
                    val isMutable = tokens.equals(i - 1, "var")
                    readDeclarationImpl(isMutable, false)
                    break
                }

                consumeIf("fun") -> readMethod() // will just get added to the scope for later resolution
                consumeIf("inner") -> throw IllegalStateException("Inner classes inside methods are not supported")
                consumeIf("enum") -> throw IllegalStateException("Enum classes inside methods are not supported")
                consumeIf("class") -> readClass(ScopeType.NORMAL_CLASS)

                language == ZauberLanguage.ZAUBER && consumeIf("defer") -> readDefer(result, origin(i - 1))
                language == ZauberLanguage.ZAUBER && consumeIf("errdefer") -> readErrdefer(result, origin(i - 1))

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

    /**
     * convert defer into try { remainder } finally { action }
     * */
    private fun readDefer(result: ArrayList<Expression>, origin: Int) {
        // todo constructor calls on values are defers, too
        //  detect them immediately or when flattening the AST?
        val action = readExpression()
        val scope = currPackage
        val subName = scope.generateName("defer", origin)
        val newScope = scope.getOrPut(subName, ScopeType.METHOD_BODY)
        currPackage = newScope
        val remainder = readMethodBody()
        if (remainder.list.isNotEmpty()) {
            val forTryBody = ArrayList(result)
            result.clear()
            val flagName = scope.generateName("deferFlag", origin)
            val flag = scope.addField(
                null, false, true, null, flagName,
                BooleanType, null, Keywords.SYNTHETIC, origin
            )
            result.add(
                TryCatchBlock(
                    ExpressionList(forTryBody, scope, origin),
                    emptyList(),
                    Finally(action, flag)
                )
            )
        } else {
            // can immediately be executed
            result.add(action)
        }
    }

    /**
     * convert errdefer into try { body } catch { action; throw e }
     * */
    private fun readErrdefer(result: ArrayList<Expression>, origin: Int) {
        val action = readExpression()
        val scope = currPackage
        val newScope = scope.generate("errdefer", ScopeType.METHOD_BODY)
        currPackage = newScope
        val errCatch = scope.generate("errdeferHandler", ScopeType.METHOD_BODY) // required for e-parameter
        val remainder = readMethodBody()
        if (remainder.list.isNotEmpty()) {
            val forTryBody = ArrayList(result)
            result.clear()

            val parameter = Parameter(0, "e", ThrowableType, errCatch, origin)
            val exceptionField = parameter.getOrCreateField(null, Keywords.NONE)
            val throwImpl = ThrowExpression(FieldExpression(exceptionField, errCatch, origin), errCatch, origin)

            result.add(
                TryCatchBlock(
                    ExpressionList(forTryBody, scope, origin),
                    listOf(Catch(parameter, ExpressionList(listOf(action, throwImpl), errCatch, origin))),
                    null
                )
            )
        } else {
            // can immediately be executed,
            //  but we don't need it, because we cannot crash on nothing
        }
    }

    private fun readDestructuringFields(): List<FieldDeclaration> {
        val names = ArrayList<FieldDeclaration>()
        pushCall {
            while (i < tokens.size) {
                val name = consumeName(VSCodeType.VARIABLE, 0)
                val type = readTypeOrNull(null)
                names.add(FieldDeclaration(name, type))
                readComma()
            }
        }
        return names
    }

    private fun readDestructuring(isMutable: Boolean, fieldScope: Scope): Expression {
        val names = readDestructuringFields()
        val value = if (consumeIf("=")) {
            readExpression()
        } else throw IllegalStateException("Expected value for destructuring at ${tokens.err(i)}")
        return createDestructuringAssignment(names, value, isMutable, fieldScope)
    }

    private fun readFieldInMethod(
        isMutable: Boolean, isLateinit: Boolean,
        fieldScope: Scope
    ): Expression {
        if (tokens.equals(i, TokenType.OPEN_CALL)) {
            check(!isLateinit) // is immediately assigned -> cannot be lateinit
            return readDestructuring(isMutable, fieldScope)
        }

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