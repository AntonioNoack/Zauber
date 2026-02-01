package me.anno.support.java.ast

import me.anno.langserver.VSCodeModifier
import me.anno.langserver.VSCodeType
import me.anno.support.cpp.ast.rich.ArrayType
import me.anno.support.cpp.ast.rich.readSwitch
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.Annotation
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
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.Import
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.AnyType
import me.anno.zauber.types.Types.ArrayType
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.impl.ClassType
import kotlin.math.max

// todo this reader is closer to C++ than Zauber, create a common class for them(?)
class JavaASTBuilder(tokens: TokenList, root: Scope) : ZauberASTBuilderBase(tokens, root, false) {

    companion object {
        private val LOGGER = LogManager.getLogger(JavaASTBuilder::class)

        val operators = me.anno.zauber.ast.rich.operators + mapOf(
            "instanceof" to Operator("instanceof", 9 /* like comparing symbols */, Assoc.LEFT),
            "?" to Operator("?", 2 /* like ?: */, Assoc.LEFT),
            "&" to Operator("&", 7, Assoc.LEFT),
            "^" to Operator("^", 6, Assoc.LEFT),
            "|" to Operator("|", 5, Assoc.LEFT),

            // between +/- and </>/<=/>=
            "<<" to Operator("<<", 10, Assoc.LEFT),
            ">>" to Operator(">>", 10, Assoc.LEFT),
            ">>>" to Operator(">>>", 10, Assoc.LEFT),
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

    override fun readSuperCalls(classScope: Scope) {
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

        if (consumeIf("permits")) {
            do {
                val type = readTypeNotNull(null, true) as ClassType
                classScope.sealedPermits.add(type)
            } while (consumeIf(","))
        }

        val addAnyIfEmpty = classScope != AnyType.clazz
        if (addAnyIfEmpty && classScope.superCalls.isEmpty()) {
            classScope.superCalls.add(SuperCall(AnyType, emptyList(), null))
        }
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

    override fun readFileLevel() {
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
                    consumeIf("static")
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

                consumeIf("enum") -> readClass(ScopeType.ENUM_CLASS)
                consumeIf("class") -> readClass(ScopeType.NORMAL_CLASS)
                consumeIf("interface") -> readInterface()
                consumeIf("record") -> {
                    keywords = keywords or Keywords.VALUE
                    readClass(ScopeType.NORMAL_CLASS)
                }

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

                consumeIf(";") -> {}// just skip it
                consumeIf("@") -> annotations.add(readAnnotation())
                consumeIf("default") -> readMethodOrFieldInClass()

                tokens.equals(i, TokenType.KEYWORD) -> collectKeywords()

                tokens.equals(i, TokenType.NAME, TokenType.KEYWORD) -> {
                    readMethodOrFieldInClass()
                }

                tokens.equals(i, "<") -> {
                    val methodScope = skipTypeParametersToFindFunctionNameAndScope(origin(i))
                    val typeParams = readTypeParameterDeclarations(methodScope)
                    val tn = readTypeAndName()
                        ?: throw IllegalStateException("Expected type and name @${tokens.err(i)}")
                    check(tokens.equals(i, "("))
                    readMethodInClass(tn.first!!, tn.second, typeParams)
                }

                else -> throw NotImplementedError("Unknown token at ${tokens.err(i)}")
            }
        }
    }

    private fun readMethodOrFieldInClass() {
        if (tokens.equals(i, currPackage.name) && tokens.equals(i + 1, TokenType.OPEN_CALL)) {
            i++ // skip class name
            // it is a constructor
            readConstructor()
        } else {
            // todo for methods, we may have generic parameters
            val tn = readTypeAndName()
                ?: throw IllegalStateException("Expected type and name @${tokens.err(i)}")
            if (tokens.equals(i, "(")) {
                readMethodInClass(tn.first!!, tn.second, emptyList())
            } else {
                readFieldInClass(tn.first!!, tn.second)
            }
        }
    }

    fun readConstructor() {
        val origin = origin(i)
        val scopeName = currPackage.generateName("constructor", origin)
        val keywords = packKeywords()
        pushScope(scopeName, ScopeType.CONSTRUCTOR) { scope ->
            val valueParameters = pushCall { readParameterDeclarations(null) }
            skipThrowList()
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
                            consume(";")
                        }
                        tokens.equals(i, "super") && tokens.equals(i + 1, TokenType.OPEN_CALL) -> {
                            val origin = origin(i)
                            consume("super")
                            val params = readValueParameters()
                            superCall = InnerSuperCall(InnerSuperCallTarget.SUPER, params, origin)
                            consume(";")
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

    fun readMethodInClass(returnType: Type, name: String, typeParameters: List<Parameter>) {
        val origin = origin(i)
        val scopeName = currPackage.generateName(name, origin)
        val keywords = packKeywords()
        pushScope(scopeName, ScopeType.METHOD) { scope ->
            val valueParameters = pushCall { readParameterDeclarations(null) }
            skipThrowList()
            val body = if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
                readBodyOrExpression(null)
            } else null
            scope.selfAsMethod = Method(
                null, false, name, typeParameters,
                valueParameters, scope, returnType, emptyList(), body, keywords, origin
            )
        }
    }

    fun skipThrowList() {
        if (consumeIf("throws")) {
            readTypeNotNull(null, true)
            while (consumeIf(",")) {
                readTypeNotNull(null, true)
            }
        }
    }

    fun readFieldInClass(valueType: Type, name: String) {
        readFieldInMethod(valueType, name, currPackage)
    }

    override fun readAnnotation(): Annotation {
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
                consumeIf("protected") -> Keywords.PROTECTED
                consumeIf("native") -> Keywords.EXTERNAL
                consumeIf("override") -> Keywords.OVERRIDE
                consumeIf("abstract") -> Keywords.ABSTRACT
                consumeIf("annotation") -> Keywords.ANNOTATION
                consumeIf("final") -> Keywords.FINAL
                consumeIf("sealed") -> Keywords.SEALED
                consumeIf("volatile") -> 0 // Keywords.VOLATILE -> todo do we need to support them?
                // todo make it synchronized: pack the body into a try-finally with lock & unlock
                consumeIf("synchronized") -> 0
                consumeIf("non-sealed") -> 0 // WTF
                // todo store that somewhere?
                consumeIf("transient") -> 0
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

            while (tokens.equals(i, TokenType.NAME, TokenType.KEYWORD) &&
                !tokens.equals(i + 1, ":")
            ) {
                keywords = keywords or when {
                    consumeIf("final") -> Keywords.FINAL
                    else -> break
                }
                setLSType(i - 1, VSCodeType.KEYWORD, 0)
            }

            val isVal = keywords.hasFlag(Keywords.FINAL)
            check(tokens.equals(i, TokenType.NAME, TokenType.KEYWORD)) {
                "Expected name, but got ${tokens.err(i)}"
            }

            val origin = origin(i)
            var type = readTypeNotNull(null, true)
            val isVararg = consumeIf("...")
            if (isVararg) type = ClassType(ArrayType.clazz, listOf(type), origin)

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

        val origin = origin(i)
        val label =
            if (tokens.equals(i, TokenType.LABEL)) tokens.toString(i++)
            else null

        return when {
            consumeIf("@", VSCodeType.DECORATOR, 0) -> {
                val annotation = readAnnotation()
                AnnotatedExpression(annotation, readPrefix())
            }
            consumeIf("null") -> SpecialValueExpression(SpecialValue.NULL, currPackage, origin)
            consumeIf("true") -> SpecialValueExpression(SpecialValue.TRUE, currPackage, origin)
            consumeIf("false") -> SpecialValueExpression(SpecialValue.FALSE, currPackage, origin)
            consumeIf("super") -> SpecialValueExpression(SpecialValue.SUPER, currPackage, origin)
            consumeIf("this") -> ThisExpression(resolveThisLabel(label), currPackage, origin)
            tokens.equals(i, TokenType.NUMBER) -> NumberExpression(tokens.toString(i++), currPackage, origin)
            tokens.equals(i, TokenType.STRING) -> StringExpression(tokens.toString(i++), currPackage, origin)
            consumeIf("return") -> readReturn(readBreakLabelName())
            consumeIf("throw") -> ThrowExpression(readExpression(), currPackage, origin)
            consumeIf("break") -> BreakExpression(readBreakLabel(), currPackage, origin)
            consumeIf("continue") -> ContinueExpression(readBreakLabel(), currPackage, origin)
            consumeIf("switch") -> readSwitch(label)
            consumeIf("if") -> readIfBranch()
            consumeIf("for") -> readForLoop(label)
            consumeIf("while") -> readWhileLoop(label)
            consumeIf("do") -> readDoWhileLoop(label)
            consumeIf("yield") -> {
                // return from a switch...
                val value = readExpression()
                val label = resolveBreakLabel(null).name
                ReturnExpression(value, label, currPackage, origin)
            }
            consumeIf("!") -> {
                val base = readExpression()
                NamedCallExpression(base, "not", currPackage, origin)
            }
            consumeIf("+") -> {
                val base = readExpression()
                NamedCallExpression(base, "unaryPlus", currPackage, origin)
            }
            consumeIf("-") -> {
                val base = readExpression()
                NamedCallExpression(base, "unaryMinus", currPackage, origin)
            }
            consumeIf("++") -> createPrefixExpression(InplaceModifyType.INCREMENT, origin, readExpression())
            consumeIf("--") -> createPrefixExpression(InplaceModifyType.DECREMENT, origin, readExpression())
            consumeIf("*") -> {
                ArrayToVarargsStar(readExpression())
            }
            consumeIf("::") -> {
                val name = consumeName(VSCodeType.METHOD, 0)
                // :: means a function of the current class, or 'new' for constructors
                DoubleColonLambda(currPackage, name, currPackage, origin)
            }

            consumeIf("new") -> {

                // todo StackTraceElement is unknown...
                //  we need to register it somehow...
                //  do we have Java sources for it, or should we load our stdlib?

                val origin = origin(i)
                val type = readTypeNotNull(null, true)
                // todo does Java support extra extends, implements?
                val isArrayType = (type is ClassType && type.clazz.pathStr == "zauber.Array") || type is ArrayType
                val values = if (!isArrayType && tokens.equals(i, TokenType.OPEN_CALL)) {
                    readValueParameters()
                } else {
                    // Arrays don't need extra parameters
                    emptyList()
                }
                if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
                    if (isArrayType) {
                        TODO("read array contents")
                    } else {
                        TODO("read inline class")
                    }
                } else {
                    when (type) {
                        is ClassType -> ConstructorExpression(
                            type.clazz, type.typeParameters,
                            values, null,
                            currPackage, origin
                        )
                        is ArrayType -> ConstructorExpression(
                            ArrayType.clazz, listOf(type.baseType),
                            listOf(NamedParameter(null, type.size)), null,
                            currPackage, origin
                        )
                        else -> throw IllegalStateException("Cannot construct $type")
                    }
                }
            }

            tokens.equals(i, TokenType.NAME) -> {
                if (tokens.equals(i + 1, "->")) {
                    readLambda(i)
                } else {
                    val origin = origin(i)
                    val vsCodeType =
                        if (tokens.equals(i + 1, TokenType.OPEN_CALL, TokenType.OPEN_BLOCK)) {
                            VSCodeType.METHOD
                        } else VSCodeType.VARIABLE
                    val namePath = consumeName(vsCodeType, 0)
                    val typeArgs = readTypeParameters(null)
                    println("reading a call, $namePath, $typeArgs")
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
            }
            tokens.equals(i, TokenType.OPEN_CALL) -> {
                // this could be a cast... it is, if (type) name
                val i0 = i
                val origin = origin(i)
                val end = tokens.findBlockEnd(i, TokenType.OPEN_CALL, TokenType.CLOSE_CALL)
                if (tokens.equals(end + 1, "->")) {
                    readLambda(end)
                } else {
                    val type = try {
                        pushCall { readType(null, true) }
                    } catch (_: IllegalStateException) {
                        null
                    }
                    if (type != null && tokens.equals(i, TokenType.NAME, TokenType.OPEN_CALL)) {
                        // open-call is just a double-cast, e.g. Long -> long -> int
                        readCastExpression(type, origin)
                    } else {
                        i = i0
                        // just something in brackets
                        pushCall { readExpression() }
                    }
                }
            }
            tokens.equals(i, "<") -> {
                val typeParameters = readTypeParameters(null)
                val methodName = consumeName(VSCodeType.METHOD, 0)
                val valueParameters = readValueParameters()
                val base = nameExpression(methodName, origin, currPackage)
                CallExpression(base, typeParameters, valueParameters, origin)
            }
            else -> {
                tokens.printTokensInBlocks(max(i - 5, 0))
                throw NotImplementedError("Unknown expression part at ${tokens.err(i)}")
            }
        }
    }

    private fun readLambdaVariables(): List<LambdaVariable> {
        val variables = ArrayList<LambdaVariable>()
        while (i < tokens.size) {
            // this is name or type + name
            val origin = origin(i)
            val justName = i + 1 == tokens.size || tokens.equals(i + 1, TokenType.COMMA)
            val name: String
            val type: Type?
            if (justName) {
                name = consumeName(VSCodeType.PARAMETER, VSCodeModifier.DECLARATION.flag)
                type = null
            } else {
                type = readTypeNotNull(null, true)
                name = consumeName(VSCodeType.PARAMETER, VSCodeModifier.DECLARATION.flag)
            }
            variables.add(createLambdaVariable(type, name, origin))
            readComma()
        }
        return variables
    }

    private fun readLambda(end: Int): Expression {
        return pushScope(ScopeType.LAMBDA, "lambda") { scope ->
            val params = if (tokens.equals(i, TokenType.OPEN_CALL)) {
                pushCall { readLambdaVariables() }
            } else {
                val origin = origin(i)
                val name = consumeName(VSCodeType.PARAMETER, VSCodeModifier.DECLARATION.flag)
                listOf(createLambdaVariable(null, name, origin))
            }

            check(i == end + 1) { "Expected $i == $end for lambda, at ${tokens.err(i)}" }
            consume("->")

            val value = readBodyOrExpression(null)
            LambdaExpression(params, scope, value)
        }
    }

    private fun readCastExpression(type: Type, origin: Int): Expression {
        val rhs = readExpression()
        return createCastExpression(rhs, currPackage, origin, type) { ifFalseScope ->
            val debugInfoExpr = StringExpression(rhs.toString(), ifFalseScope, origin)
            val debugInfoParam = NamedParameter(null, debugInfoExpr)
            CallExpression(
                UnresolvedFieldExpression("throwNPE", shouldBeResolvable, ifFalseScope, origin),
                emptyList(), listOf(debugInfoParam), origin
            )
        }
    }

    private fun readForLoop(label: String?): Expression {
        return pushScope(ScopeType.METHOD_BODY, "for") { scope ->
            lateinit var initial: Expression
            lateinit var condition: Expression
            lateinit var increment: Expression
            lateinit var field: Field
            var isIterator = false
            val origin = origin(i - 1)
            pushCall {
                val k = i
                val fieldIsFinal = consumeIf("final")
                val tn = readTypeAndName()
                if (tn != null && tokens.equals(i, ":")) {
                    field = scope.addField(
                        null, false, !fieldIsFinal, null,
                        tn.second, tn.first, null, Keywords.NONE, origin
                    )
                    consume(":")
                    initial = readExpression()
                    isIterator = true
                } else {
                    i = k
                    initial = readExpressionOrNullWithSemicolon() ?: unitInstance
                    condition = readExpressionOrNullWithSemicolon()
                        ?: SpecialValueExpression(SpecialValue.TRUE, currPackage, origin(i - 1))
                    increment = readExpressionOrNullWithSemicolon() ?: unitInstance
                }
            }
            val body = readBodyOrExpression(label ?: "")
            if (isIterator) {
                forLoop(field, initial, body, label)
            } else {
                val body = ExpressionList(listOf(body, increment), currPackage, origin)
                val result = ArrayList<Expression>()
                if (initial != unitInstance) result.add(initial)
                result.add(WhileLoop(condition, body, label))
                ExpressionList(result, currPackage, origin)
            }
        }
    }

    private fun readExpressionOrNullWithSemicolon(): Expression? {
        if (consumeIf(";")) return null
        val k = i
        val tn = readTypeAndName()
        val expr = if (tn != null) {
            readFieldInMethod(tn.first, tn.second, currPackage)
        } else {
            i = k
            readExpression()
        }
        if (i < tokens.size && !tokens.equals(i - 1, TokenType.SEMICOLON)) {
            consume(";")
        }
        return expr
    }

    private fun readExpressionWithSemicolon(): Expression {
        val expr = readExpression()
        consume(";")
        return expr
    }

    private fun readReturn(label: String?): ReturnExpression {
        val origin = origin(i - 1)
        val value = if (i < tokens.size && !tokens.equals(i, TokenType.SEMICOLON)) readExpression() else unitInstance
        return ReturnExpression(value, label, currPackage, origin)
    }

    private fun findExpressionEnd(): Int {
        var depth = 0
        var j = i
        while (j < tokens.size) {
            when (tokens.getType(j)) {
                TokenType.OPEN_CALL, TokenType.OPEN_BLOCK, TokenType.OPEN_ARRAY -> depth++
                TokenType.CLOSE_CALL, TokenType.CLOSE_BLOCK, TokenType.CLOSE_ARRAY -> {
                    if (depth == 0) return j
                    depth--
                }
                TokenType.SEMICOLON, TokenType.COMMA -> {
                    if (depth == 0) return j
                }
                else -> if (depth == 0) {
                    /*if (tokens.equals(j, "if", "else", "for", "do", "while")) {
                        return j
                    }*/
                    // ':' is only allowed, if '?' came before...
                    // '->' is only allowed, if for a lambda...
                }
            }
            j++
        }
        return j
    }

    override fun readExpression(minPrecedence: Int): Expression {
        // println("reading expr at ${tokens.err(i)}")
        return tokens.push(findExpressionEnd()) {
            readExpressionImpl(minPrecedence)
        }
    }

    private fun readExpressionImpl(minPrecedence: Int): Expression {
        println("reading expr at ${tokens.err(i)}")
        var expr = readPrefix()
        if (LOGGER.isDebugEnabled) LOGGER.debug("prefix: $expr")

        // main elements
        loop@ while (i < tokens.size) {
            println("next token: ${tokens.err(i)}")
            val symbol = when (tokens.getType(i)) {
                TokenType.SYMBOL, TokenType.KEYWORD -> tokens.toString(i)
                TokenType.NAME -> break@loop
                TokenType.APPEND_STRING -> "+"
                else -> {
                    // postfix
                    println("reading postfix for $expr")
                    expr = tryReadPostfix(expr) ?: break@loop
                    continue@loop
                }
            }

            if (LOGGER.isDebugEnabled) LOGGER.debug("symbol $symbol, valid? ${symbol in operators}")

            val op = operators[symbol]
            if (op == null) {
                // postfix
                println("binary[$symbol] -> null")
                expr = tryReadPostfix(expr) ?: break@loop
            } else {

                // todo it could be a lambda:
                //  name -> ...
                //  or (names,) -> ...

                if (op.precedence < minPrecedence) break@loop

                val origin = origin(i)
                i++ // consume operator

                val scope = currPackage
                println("binary[$symbol]")
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
                    "." -> {
                        if (consumeIf("class")) {
                            val type = when (expr) {
                                is TypeExpression -> expr.type
                                is UnresolvedFieldExpression -> currPackage.resolveType(expr.name, imports)
                                else -> throw IllegalStateException("$expr (${expr.javaClass.simpleName}) is a type...")
                            }
                            GetClassFromTypeExpression(type, scope, origin)
                        } else {
                            handleDotOperator(expr)
                        }
                    }
                    "&&", "||" -> handleShortcutOperator(expr, symbol, op, scope, origin)
                    "::" -> {
                        val rhs = if (consumeIf("new")) {
                            UnresolvedFieldExpression("new", emptyList(), scope, origin)
                        } else {
                            readRHS(op)
                        }
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
        return if (consumeIf("@")) {
            check(tokens.equals(i, TokenType.NAME, TokenType.KEYWORD)) {
                "Expected name for label, got ${tokens.err(i)}"
            }
            tokens.toString(i++)
        } else null
    }

    private fun readSynchronized(): Expression {
        // store lock in a temporary field,
        //  create try-finally for lock & unlock
        val origin = origin(i - 1)
        val lock = pushCall { readExpression() }
        val scope = currPackage
        val tmpField = scope.createImmutableField(lock)
        val scopeName = scope.generateName("sync", origin)
        val body = pushBlock(ScopeType.METHOD_BODY, scopeName) {
            readMethodBody()
        }
        val lockMember = UnresolvedFieldExpression("lock", emptyList(), scope, origin)
        val unlockMember = UnresolvedFieldExpression("unlock", emptyList(), scope, origin)
        val tmpFieldExpr = FieldExpression(tmpField, scope, origin)
        val tmpFieldParam = listOf(NamedParameter(null, tmpFieldExpr))
        val lockExpr = CallExpression(lockMember, emptyList(), tmpFieldParam, origin)
        val unlockExpr = CallExpression(unlockMember, emptyList(), tmpFieldParam, origin)
        val assignmentExpr = AssignmentExpression(tmpFieldExpr, lock)
        val bodyPlusLock = ExpressionList(listOf(assignmentExpr, lockExpr, body), scope, origin)
        val flagName = scope.generateName("deferFlag", origin)
        val flag = scope.addField(
            null, false, true, null, flagName,
            BooleanType, null, Keywords.SYNTHETIC, origin
        )
        return TryCatchBlock(bodyPlusLock, emptyList(), Finally(unlockExpr, flag))
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
                consumeIf("switch") -> result += readSwitch(null)
                consumeIf("try") -> result += readTryCatch()
                consumeIf("synchronized") -> result += readSynchronized()
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
                    consume(";")
                }
                consumeIf("throw") -> {
                    val origin = origin(i - 1)
                    result += ThrowExpression(readExpressionWithSemicolon(), currPackage, origin)
                }
                consumeIf("assert") -> {
                    val origin = origin(i - 1)
                    val expr = readExpression()
                    val message = if (consumeIf(":")) {
                        readExpression()
                    } else null
                    consume(";")

                    val checkName = UnresolvedFieldExpression("check", emptyList(), currPackage, origin)
                    val params = if (message == null) {
                        listOf(NamedParameter(null, expr))
                    } else {
                        val lambda = LambdaExpression(emptyList(), currPackage, message)
                        listOf(NamedParameter(null, expr), NamedParameter(null, lambda))
                    }
                    result += CallExpression(checkName, emptyList(), params, origin)
                }

                tokens.equals(i, TokenType.NAME) && tokens.equals(i + 1, "@") &&
                        tokens.equals(i + 2, TokenType.KEYWORD) -> {
                    val label = tokens.toString(i++)
                    consume("@")
                    result += when {
                        consumeIf("do") -> readDoWhileLoop(label)
                        consumeIf("while") -> readWhileLoop(label)
                        consumeIf("for") -> readForLoop(label)
                        consumeIf("switch") -> readSwitch(label)
                        else -> throw IllegalStateException("Unknown $label@${tokens.err(i)}")
                    }
                }

                consumeIf("var", VSCodeType.KEYWORD, 0) -> {
                    val name = consumeName(VSCodeType.VARIABLE, VSCodeModifier.DECLARATION.flag)
                    result += readDeclaration(null, name)
                }

                consumeIf("final") -> {
                    val k = i
                    val tn = readTypeAndName()
                        ?: throw IllegalStateException("Expected type and name after 'final' at ${tokens.err(k)}")
                    result += readDeclaration(tn.first, tn.second)
                    while (consumeIf(",")) {
                        val name = consumeName(VSCodeType.VARIABLE, VSCodeModifier.DECLARATION.flag)
                        result += readDeclaration(tn.first, name)
                    }
                }

                tokens.equals(i, TokenType.OPEN_BLOCK) -> {
                    // blocks can appear at any time in Java...
                    pushBlock(ScopeType.METHOD_BODY, null) {
                        readMethodBody()
                    }
                }

                else -> {
                    val k = i
                    val tn = readTypeAndName()
                    println("type & name: $tn, $i vs $k")
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
        // println("finished reading body, $i vs ${tokens.size}, ${tokens.err(i)}")
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

        val assignments = ArrayList<Expression>()
        var name = name

        do {
            val initialValue = if (consumeIf("=")) readExpression() else null
            check(type != null || initialValue != null) { "Field at ${tokens.err(i0)} either needs a type or a value" }

            // define variable in the scope
            val field = fieldScope.addField(
                null, false, isMutable = isMutable, null,
                name, type, initialValue, keywords, origin
            )

            if (initialValue != null) {
                val variableName = FieldExpression(field, currPackage, origin)
                assignments += AssignmentExpression(variableName, initialValue)
            }

            if (consumeIf(",")) {
                name = consumeName(VSCodeType.VARIABLE, VSCodeModifier.DECLARATION.flag)
            } else break
        } while (true)
        consume(";")

        return ExpressionList(assignments, currPackage, origin)
    }

    private fun readTypeAndName(): Pair<Type?, String>? {
        val i0 = i
        val tn = readTypeAndNameImpl()
        if (tn == null) i = i0
        return tn
    }

    private fun readTypeAndNameImpl(): Pair<Type?, String>? {
        // early exit
        if (tokens.equals(i + 1, TokenType.COMMA, TokenType.SEMICOLON) ||
            tokens.equals(i + 1, TokenType.OPEN_CALL)
        ) return null

        try {
            val type = if (consumeIf("var")) null else {
                readType(null, true) ?: return null
            }
            if (!tokens.equals(i, TokenType.NAME)) return null
            val name = tokens.toString(i++)
            return type to name
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            return null
        }
    }
}