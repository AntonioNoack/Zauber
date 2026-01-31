package me.anno.support.java.ast

import me.anno.langserver.VSCodeModifier
import me.anno.langserver.VSCodeType
import me.anno.zauber.ast.KeywordSet
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.Annotation
import me.anno.zauber.ast.rich.DataClassGenerator.finishDataClass
import me.anno.zauber.ast.rich.Keywords.hasFlag
import me.anno.zauber.ast.rich.ScopeSplit.shouldSplitIntoSubScope
import me.anno.zauber.ast.rich.ScopeSplit.splitIntoSubScope
import me.anno.zauber.ast.rich.ZauberASTBuilder.Companion.debug
import me.anno.zauber.ast.rich.ZauberASTBuilder.Companion.unitInstance
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
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.Import
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.AnyType
import me.anno.zauber.types.Types.ArrayType
import me.anno.zauber.types.Types.ListType
import me.anno.zauber.types.impl.ClassType
import kotlin.math.max
import kotlin.math.min

class JavaASTBuilder(tokens: TokenList, root: Scope) : ZauberASTBuilderBase(tokens, root, false) {

    companion object {
        private val LOGGER = LogManager.getLogger(JavaASTBuilder::class)

        val operators = me.anno.zauber.ast.rich.operators + mapOf(
            "instanceof" to Operator("instanceof", 35 /* extra strong? */, Assoc.LEFT),
            "?" to Operator("?", 2 /* like ?: */, Assoc.LEFT),
        )
    }

    val lsTypes = IntArray(tokens.size).apply { fill(-1) }
    val lsModifiers = IntArray(tokens.size)

    // todo use this to put things into companion object instead of the class itself
    var isStatic = false

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
        val constructorBody = ExpressionList(ArrayList(), classScope, origin)

        readSuperCalls(classScope)

        val primConstructorScope = classScope.getOrCreatePrimConstructorScope()
        val primarySuperCall = classScope.superCalls.firstOrNull { it.valueParameters != null }
        val primaryConstructor = Constructor(
            emptyList(),
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

    private fun getStaticScope(): Scope {
        check(currPackage.isClassType()) {
            "Base class for static scope was not a class $currPackage"
        }
        val co = currPackage.companionObject
        if (co != null) return co

        return pushScope("Companion", ScopeType.COMPANION_OBJECT) { scope -> scope }
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

                consumeIf("interface") -> readInterface()

                tokens.equals(i, "static") && tokens.equals(i + 1, TokenType.OPEN_BLOCK) -> {
                    i++ // skip 'static'
                    val staticScope = getStaticScope()
                    pushScope(staticScope) {
                        pushBlock(staticScope.getOrCreatePrimConstructorScope()) {
                            readMethodBody()
                        }
                    }
                }

                tokens.equals(i, TokenType.OPEN_BLOCK) -> {
                    // init block
                    pushBlock(currPackage.getOrCreatePrimConstructorScope()) {
                        readMethodBody()
                    }
                }

                consumeIf("@") -> annotations.add(readAnnotation())

                tokens.equals(i, TokenType.KEYWORD) -> collectKeywords()

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

                consumeIf(";") -> {}// just skip it
                tokens.equals(i, TokenType.NAME) -> {
                    if (tokens.equals(i, currPackage.name) && tokens.equals(i + 1, TokenType.OPEN_CALL)) {
                        i++ // skip class name
                        // it is a constructor
                        readConstructor()
                    } else {
                        // todo for methods, we may have generic parameters
                        val tn = readTypeAndName()
                            ?: throw IllegalStateException("Expected type and name @${tokens.err(i)}")
                        if (tokens.equals(i, "(")) {
                            readMethodInClass(tn.first, tn.second)
                        } else {
                            readFieldInClass(tn.first, tn.second)
                        }
                    }
                }

                else -> throw NotImplementedError("Unknown token at ${tokens.err(i)}")
            }
        }
    }

    fun readConstructor() {
        val origin = origin(i)
        val scopeName = currPackage.generateName("constructor", origin)
        val keywords = packKeywords()
        pushScope(scopeName, ScopeType.CONSTRUCTOR) { scope ->
            val valueParameters = pushCall { readParameterDeclarations(null) }
            var superCall: InnerSuperCall? = null
            val body = if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
                pushBlock(ScopeType.METHOD, "body") {
                    // read the super call
                    when {
                        tokens.equals(i, "this") && tokens.equals(i + 1, TokenType.OPEN_CALL) -> {
                            val origin = origin(i)
                            consume("this")
                            val params = readValueParameters()
                            superCall = InnerSuperCall(InnerSuperCallTarget.THIS, params, origin)
                        }
                        tokens.equals(i, "super") && tokens.equals(i + 1, TokenType.OPEN_CALL) -> {
                            val origin = origin(i)
                            consume("super")
                            val params = readValueParameters()
                            superCall = InnerSuperCall(InnerSuperCallTarget.SUPER, params, origin)
                        }
                    }
                    // then the remainder
                    readMethodBody()
                }
            } else ExpressionList(ArrayList(), scope, origin(i))
            scope.selfAsConstructor = Constructor(
                valueParameters, scope, superCall,
                body, keywords, origin
            )
        }
    }

    fun readMethodInClass(returnType: Type, name: String) {
        val origin = origin(i)
        val scopeName = currPackage.generateName(name, origin)
        val keywords = packKeywords()
        pushScope(scopeName, ScopeType.METHOD) { scope ->
            val valueParameters = pushCall { readParameterDeclarations(null) }
            val body = if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
                readBodyOrExpression(null)
            } else null
            scope.selfAsMethod = Method(
                null, false, name, emptyList(),
                valueParameters, scope, returnType, emptyList(), body, keywords, origin
            )
        }
    }

    fun readFieldInClass(valueType: Type, name: String) {
        readFieldInMethod(valueType, name, currPackage)
    }

    fun readAnnotation(): Annotation {
        if (tokens.equals(i, TokenType.NAME) &&
            tokens.equals(i + 1, ":") &&
            tokens.equals(i + 2, TokenType.NAME)
        ) {
            // skipping scope
            i += 2
        }

        if (tokens.equals(i, "Override") && !tokens.equals(i + 1, TokenType.OPEN_CALL)) {
            i++ // skip 'Override'
            keywords = keywords or Keywords.OVERRIDE
            val type = langScope.getOrPut("Override", ScopeType.INTERFACE).typeWithArgs
            return Annotation(type, emptyList())
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
                consumeIf("native") -> Keywords.EXTERNAL
                consumeIf("override") -> Keywords.OVERRIDE
                consumeIf("abstract") -> Keywords.ABSTRACT
                consumeIf("data") -> Keywords.DATA_CLASS
                consumeIf("value") -> Keywords.VALUE
                consumeIf("annotation") -> Keywords.ANNOTATION
                consumeIf("final") -> Keywords.FINAL
                consumeIf("static") -> {
                    isStatic = true
                    0
                }
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

            // println("Found $name: $type = $initialValue at ${resolveOrigin(i)}")

            val keywords = packKeywords()
            val parameter = Parameter(
                parameters.size, !isVal, isVal, isVararg, name, type,
                null, currPackage, origin
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

            consumeIf("new") -> {
                val origin = origin(i)
                val type = readTypeNotNull(null, true)
                // todo does Java support extra extends, implements?
                val values = readValueParameters()
                if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
                    TODO("read inline class")
                } else {
                    type as ClassType
                    ConstructorExpression(
                        type.clazz, type.typeParameters,
                        values, null,
                        currPackage, origin
                    )
                }
            }

            tokens.equals(i, TokenType.NAME) -> {
                val origin = origin(i)
                val vsCodeType =
                    if (tokens.equals(i + 1, TokenType.OPEN_CALL, TokenType.OPEN_BLOCK)) {
                        VSCodeType.METHOD
                    } else VSCodeType.VARIABLE
                val namePath = consumeName(vsCodeType, 0)
                val typeArgs = readTypeParameters(null)
                if (tokens.equals(i, TokenType.OPEN_CALL)) {
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
                } else {
                    nameExpression(namePath, origin, currPackage)
                }
            }
            tokens.equals(i, TokenType.OPEN_CALL) -> {
                // this could be a cast... it is, if (type) name
                val i0 = i
                val origin = origin(i)
                var type: Type? = null
                val hasType = pushCall {
                    type = readType(null, true)
                    val hasType = type != null && i == tokens.size
                    i = tokens.size // prevent crash, because we didn't consume all tokens
                    hasType
                }
                if (hasType && tokens.equals(i, TokenType.NAME)) {
                    val name = consumeName(VSCodeType.VARIABLE, 0)
                    val expr = nameExpression(name, origin(i - 1), currPackage)
                    createCastExpression(expr, currPackage, origin, type!!) { ifFalseScope ->
                        val debugInfoExpr = StringExpression(expr.toString(), ifFalseScope, origin)
                        val debugInfoParam = NamedParameter(null, debugInfoExpr)
                        CallExpression(
                            UnresolvedFieldExpression("throwNPE", shouldBeResolvable, ifFalseScope, origin),
                            emptyList(), listOf(debugInfoParam), origin
                        )
                    }
                } else {
                    i = i0
                    // just something in brackets
                    pushCall { readExpression() }
                }
            }
            else -> {
                tokens.printTokensInBlocks(max(i - 5, 0))
                throw NotImplementedError("Unknown expression part at ${tokens.err(i)}")
            }
        }
    }

    private fun readSwitchCase(label: String?): Expression {
        TODO("read switch-case like C++")
    }

    private fun readForLoop(label: String?): Expression {
        return pushScope(ScopeType.METHOD_BODY, "for") {
            lateinit var initial: Expression
            lateinit var condition: Expression
            lateinit var increment: Expression
            val origin = origin(i - 1)
            pushCall {
                // todo actually we expect a declaration
                // todo we must also support for(name: values)
                initial = readExpressionOrNullWithSemicolon() ?: unitInstance
                condition = readExpressionOrNullWithSemicolon()
                    ?: SpecialValueExpression(SpecialValue.TRUE, currPackage, origin(i - 1))
                increment = readExpressionOrNullWithSemicolon() ?: unitInstance
            }
            val body = pushScope(ScopeType.METHOD_BODY, "forBody") {
                val body = readMethodBody()
                ExpressionList(listOf(body, increment), currPackage, origin)
            }
            val result = ArrayList<Expression>()
            if (initial != unitInstance) result.add(initial)
            result.add(WhileLoop(condition, body, label))
            ExpressionList(result, currPackage, origin)
        }
    }

    private fun readExpressionOrNullWithSemicolon(): Expression? {
        if (consumeIf(";")) return null
        val expr = readExpression()
        consume(";")
        return expr
    }

    private fun readExpressionWithSemicolon(): Expression {
        val expr = readExpression()
        consume(";")
        return expr
    }

    private fun readReturn(label: String?): ReturnExpression {
        val origin = origin(i - 1)
        val value = if (!tokens.equals(i, ",", ";")) readExpressionWithSemicolon() else unitInstance
        return ReturnExpression(value, label, currPackage, origin)
    }

    private fun findExpressionEnd(): Int {
        var depth = 0
        var j = i
        while (j < tokens.size) {
            when (tokens.getType(j)) {
                TokenType.OPEN_CALL, TokenType.OPEN_BLOCK, TokenType.OPEN_ARRAY -> depth++
                TokenType.CLOSE_CALL, TokenType.CLOSE_BLOCK, TokenType.CLOSE_ARRAY -> {
                    depth--
                    if (depth < 0) return j
                }
                TokenType.SEMICOLON -> if (depth == 0) return j
                else -> if (depth == 0) {
                    if (tokens.equals(j, "if", "else", "for", "do", "while")) {
                        return j
                    }
                }
            }
            j++
        }
        return j
    }

    override fun readExpression(minPrecedence: Int): Expression {
        tokens.push(findExpressionEnd()) {
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

                    // todo it could be a lambda:
                    //  name -> ...
                    //  or (names,) -> ...

                    if (op.precedence < minPrecedence) break@loop

                    val origin = origin(i)
                    i++ // consume operator

                    val scope = currPackage
                    expr = when (symbol) {
                        "instanceof" -> {
                            val type = readTypeNotNull(null, true)
                            val base = IsInstanceOfExpr(expr, type, scope, origin)
                            if (tokens.equals(i, TokenType.NAME)) {
                                // assign to temporary field to avoid double-invocation
                                val tmpField = currPackage.createImmutableField(base.value)
                                val tmpExpr = FieldExpression(tmpField, currPackage, origin)
                                val baseWithTmp = base.withValue(tmpExpr)
                                NamedCastExpression(baseWithTmp, tokens.toString(i++))
                            } else base
                        }
                        // todo validate that this is sufficient for elvis expressions
                        "?" -> createBranchExpression(
                            expr, scope, origin,
                            { fieldExpr -> fieldExpr },
                            { _, scope ->
                                pushScope(scope) { readExpression() }
                            },
                            { scope ->
                                consume(":")
                                pushScope(scope) { readExpression() }
                            },
                        )
                        "." -> handleDotOperator(expr)
                        "&&", "||" -> {
                            val name = currPackage.generateName("shortcut", origin)
                            val right = pushScope(name, ScopeType.METHOD_BODY) { readRHS(op) }
                            if (symbol == "&&") shortcutExpressionI(expr, ShortcutOperator.AND, right, scope, origin)
                            else shortcutExpressionI(expr, ShortcutOperator.OR, right, scope, origin)
                        }
                        "::" -> {
                            val rhs = readRHS(op)
                            binaryOp(currPackage, expr, op.symbol, rhs)
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
    }

    private fun tryReadPostfix(expr: Expression): Expression? {
        return when {
            i >= tokens.size -> null
            tokens.equals(i, TokenType.OPEN_CALL) -> {
                val origin = origin(i)
                val params = readValueParameters()
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
            consumeIf("++") -> createPostfixExpression(expr, InplaceModifyType.INCREMENT, origin(i - 1))
            consumeIf("--") -> createPostfixExpression(expr, InplaceModifyType.DECREMENT, origin(i - 1))
            else -> null
        }
    }

    private fun readBreakLabel(): Scope {
        return resolveBreakLabel(readBreakLabelName())
    }

    private fun readBreakLabelName(): String? {
        i++
        return if (consumeIf("@")) {
            check(tokens.equals(i, TokenType.NAME)) {
                "Expected name for label, got ${tokens.err(i)}"
            }
            tokens.toString(i++)
        } else null
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

            println("Reading method body, ${tokens.err(i)}")

            when {
                consumeIf(";") -> {} // skip
                consumeIf("@") -> annotations.add(readAnnotation())
                consumeIf("if") -> result += readIfBranch()
                consumeIf("do") -> result += readDoWhileLoop(null)
                consumeIf("while") -> result += readWhileLoop(null)
                consumeIf("for") -> result += readForLoop(null)
                consumeIf("switch") -> result += readSwitchCase(null)
                consumeIf("try") -> result += readTryCatch()
                consumeIf("break") -> {
                    val origin = origin(i - 1)
                    result += BreakExpression(readBreakLabel(), currPackage, origin)
                    consume(";")
                }
                consumeIf("continue") -> {
                    val origin = origin(i - 1)
                    result += ContinueExpression(readBreakLabel(), currPackage, origin)
                    consume(";")
                }
                consumeIf("return") -> {
                    result += readReturn(readBreakLabelName())
                }
                consumeIf("throw") -> {
                    val origin = origin(i - 1)
                    result += ThrowExpression(readExpressionWithSemicolon(), currPackage, origin)
                }

                tokens.equals(i, TokenType.NAME) && tokens.equals(i + 1, "@") &&
                        tokens.equals(i + 2, TokenType.KEYWORD) -> {
                    val label = tokens.toString(i++)
                    consume("@")
                    result += when {
                        consumeIf("do") -> readDoWhileLoop(label)
                        consumeIf("while") -> readWhileLoop(label)
                        consumeIf("for") -> readForLoop(label)
                        consumeIf("switch") -> readSwitchCase(label)
                        else -> throw IllegalStateException("Unknown $label@${tokens.err(i)}")
                    }
                }

                // todo we must skip some stuff, before we know whether we read a field declaration,
                //  or just some assignment, or call
                // todo methods inside methods aren't supported anyway

                consumeIf("var", VSCodeType.KEYWORD, 0) -> {
                    val name = consumeName(VSCodeType.VARIABLE, VSCodeModifier.DECLARATION.flag)
                    result += readDeclaration(null, name)
                }

                else -> {
                    val tn = readTypeAndName()
                    if (tn != null) {
                        result += readDeclaration(tn.first, tn.second)
                        while (consumeIf(",")) {
                            val name = consumeName(VSCodeType.VARIABLE, VSCodeModifier.DECLARATION.flag)
                            result += readDeclaration(tn.first, name)
                        }
                    } else {
                        result.add(readExpression())
                        consume(";")
                        if (LOGGER.isDebugEnabled) LOGGER.debug("block += ${result.last()}")
                    }
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

    private fun readDeclaration(type: Type?, name: String): ExpressionList {
        val oldScope = currPackage
        val subName = oldScope.generateName("split")
        val newScope = oldScope.getOrPut(subName, ScopeType.METHOD_BODY)
        // field shall be declared in newScope, but expr shall be in old scope...
        val declaration = readFieldInMethod(type, name, newScope)
        return pushScope(newScope) {
            val remainder = readMethodBody()
            (remainder.list as ArrayList<Expression>).add(0, declaration)
            remainder
        }
    }

    private fun readFieldInMethod(
        type: Type?, name: String,
        fieldScope: Scope
    ): Expression {

        val i0 = i
        val origin = origin(i0)
        val isMutable = !keywords.hasFlag(Keywords.FINAL)

        val keywords = packKeywords()

        if (LOGGER.isDebugEnabled) LOGGER.debug("reading var/val $name")

        val initialValue = if (consumeIf("=")) readExpression() else null
        check(type != null || initialValue != null) { "Field at ${tokens.err(i0)} either needs a type or a value" }
        consume(";")

        // define variable in the scope
        val field = fieldScope.addField(
            null, false, isMutable = isMutable, null,
            name, type, initialValue, keywords, origin
        )

        return createDeclarationExpression(fieldScope, initialValue, field)
    }

    private fun readTypeAndName(): Pair<Type, String>? {
        val type = readType(null, true) ?: return null
        if (!tokens.equals(i, TokenType.NAME)) return null
        val name = tokens.toString(i++)
        return type to name
    }
}