package me.anno.support.cpp.ast.rich

import me.anno.support.cpp.ast.rich.PointerType.Companion.ptr
import me.anno.support.java.ast.JavaASTBuilder
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.ZauberASTBuilder.Companion.debug
import me.anno.zauber.ast.rich.ZauberASTBuilder.Companion.unitInstance
import me.anno.zauber.ast.rich.controlflow.*
import me.anno.zauber.ast.rich.expression.*
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.ast.rich.expression.unresolved.*
import me.anno.zauber.logging.LogManager
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.types.BooleanUtils.not
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.ByteType
import me.anno.zauber.types.Types.DoubleType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.LongType
import me.anno.zauber.types.Types.UnitType
import me.anno.zauber.types.impl.ClassType

class CppASTBuilder(
    tokens: TokenList,
    root: Scope,
    val standard: CppStandard
) : JavaASTBuilder(tokens, root) {

    companion object {
        private val LOGGER = LogManager.getLogger(CppASTBuilder::class)

        private val builtInTypes = mapOf(
            "int" to IntType,
            "long" to LongType,
            "float" to FloatType,
            "double" to DoubleType,
            "bool" to BooleanType,
            "char" to ByteType,
            "void" to UnitType,
        )
    }

    val language = standard.kind()

    override fun readFileLevel() {
        while (i < tokens.size) {
            // println("Reading file $i < ${tokens.size}")
            when {
                consumeIf("namespace") -> readNamespace()
                consumeIf("class") -> readStructOrClass(false)
                consumeIf("struct") -> readStructOrClass(true)
                consumeIf("enum") -> readEnum()
                consumeIf("using") -> {
                    consume("namespace")
                    TODO("read this as an import with star")
                }
                consumeIf("typedef") -> readTypeAlias()
                else -> readFieldOrMethod()
            }
        }
    }

    fun readFieldOrMethod() {
        var end = i
        while (end < tokens.size) {
            when {
                tokens.equals(end, ";") || tokens.equals(end, "=") -> {
                    readField(end, true)
                    return
                }
                tokens.equals(end, "(") -> {
                    readMethod(end)
                    return
                }
            }
            end++
        }

        throw IllegalStateException("Unexpected EOF for field/method at ${tokens.err(i)}")
    }

    fun readTypeUntil(endExcl: Int): Type {
        println("Reading type $i .. $endExcl")
        val originalSize = tokens.size
        tokens.size = endExcl
        val type = readTypeImpl()
        tokens.size = originalSize
        return type
    }

    fun readTypeImpl(): Type {
        var type = readTypePath(null)
        loop@ while (true) {
            if (consumeIf("* ")) {
                type = type.ptr()
                continue@loop
            }
            if (tokens.equals(i, TokenType.OPEN_ARRAY)) {
                val size = pushArray { readExpression() }
                type = ArrayType(type, size)
                continue@loop
            }
            break
        }
        return type
    }

    override fun readTypePath(selfType: Type?): Type {
        check(tokens.equals(i, TokenType.NAME) || tokens.equals(i, TokenType.KEYWORD)) {
            "Expected name or keyword for type, but got ${tokens.err(i)}, $i vs ${tokens.size}"
        }

        val name0 = tokens.toString(i++)
        var path = builtInTypes[name0]
            ?: genericParams.last()[name0]
            ?: currPackage.resolveTypeOrNull(name0, this)
            ?: (selfType as? ClassType)?.clazz?.resolveType(name0, this)
            ?: throw IllegalStateException("Missing type '$name0'")

        while (tokens.equals(i, "::") && tokens.equals(i + 1, TokenType.NAME)) {
            path = (path as ClassType).clazz.getOrPut(tokens.toString(i + 1), null).typeWithoutArgs
            i += 2 // skip period and name
        }
        return path
    }

    override fun readSuperCalls(classScope: Scope) {
        TODO("Not yet implemented")
    }

    // todo name can have additional stars
    fun readTypeAndName(typeNameEnd: Int): Pair<Type, String> {
        // consumeKeyword()
        val typeEnd = typeNameEnd - 1
        //check(i < typeEnd) { "Not enough tokens for type and name at ${tokens.err(i)} .. $typeNameEnd" }
        check(tokens.equals(typeEnd, TokenType.NAME)) {
            "Expected field name at ${tokens.err(typeEnd)}"
        }
        val name = tokens.toString(typeEnd)
        val type = readTypeUntil(typeEnd)

        i = typeNameEnd
        return type to name
    }

    fun readField(typeNameEnd: Int, skipEnd: Boolean): Field {
        val origin = origin(i)
        val (type, name) = readTypeAndName(typeNameEnd)
        val initialValue = if (consumeIf("=")) readExpression() else null

        val field = currPackage.addField(
            null, false, isMutable = true, null,
            name, type, initialValue, packKeywords(), origin
        )

        if (skipEnd) check(tokens.equals(i++, ";", ",", ")"))
        return field
    }

    private fun readParameters(): List<Parameter> {
        // special case for a list without arguments
        if (i + 1 == tokens.size && consumeIf("void")) {
            return emptyList()
        }
        val parameters = ArrayList<Parameter>()
        while (i < tokens.size) {
            var end = i
            var depth = 0
            while (end < tokens.size) {
                when {
                    (tokens.equals(end, ",", "(")) && depth == 0 -> break
                    tokens.equals(end, "(", "[", "{") -> depth++
                    tokens.equals(end, ")", "]", "}") -> depth--
                }
                end++
            }
            val origin = origin(i)
            val field = readField(end, false)
            parameters.add(Parameter(parameters.size, field.name, field.valueType!!, currPackage, origin))
            readComma()
        }
        return parameters
    }

    fun readMethod(typeNameEnd: Int): Method {
        val origin = origin(i)
        val (returnType, name) = readTypeAndName(typeNameEnd)
        val genName = if (language == LanguageKind.C) name else currPackage.generateName("f:$name")
        val methodScope = currPackage.getOrPut(genName, ScopeType.METHOD)
        val keywords = packKeywords()
        val arguments = pushScope(methodScope) {
            pushCall { readParameters() }
        }
        val body = if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
            pushBlock(methodScope) {
                readBodyOrExpression()
            }
        } else {
            consume(";")
            null
        }
        // todo find self type, for fields, too
        val method = Method(
            null, false, name, emptyList(), arguments,
            methodScope, returnType, emptyList(), body, keywords, origin
        )
        methodScope.selfAsMethod = method
        return method
    }

    fun readEnum() {
        check(tokens.equals(i, TokenType.NAME)) {
            "Expected enum type name at ${tokens.err(i)}"
        }
        val enumName = tokens.toString(i++)
        pushScope(enumName, ScopeType.ENUM_CLASS) { classScope ->
            classScope.hasTypeParameters = true
            var ordinal = 0 // todo use value instead???
            pushBlock(classScope) {
                while (i < tokens.size) {
                    check(tokens.equals(i, TokenType.NAME)) {
                        "Expected enum entry name at ${tokens.err(i)}"
                    }
                    val origin = origin(i)
                    val valueName = tokens.toString(i++)
                    val value = if (consumeIf("=")) {
                        readExpression()
                    } else null

                    val keywords = packKeywords()
                    val entryScope = classScope.getOrPut(valueName, ScopeType.ENUM_ENTRY_CLASS)
                    entryScope.hasTypeParameters = true

                    // todo avoid duplicates?
                    val numberExpr = value ?: NumberExpression((ordinal++).toString(), classScope, origin)
                    val extraValueParameters = listOf(
                        NamedParameter(null, numberExpr),
                        NamedParameter(null, StringExpression(valueName, classScope, origin)),
                    )
                    val initialValue = ConstructorExpression(
                        classScope, emptyList(),
                        extraValueParameters,
                        null, classScope, origin
                    )
                    entryScope.objectField = classScope.addField(
                        null, false, isMutable = false, null,
                        valueName, classScope.typeWithoutArgs, initialValue, keywords, origin
                    )

                    readComma()
                }
            }
        }
    }

    fun readStructOrClass(isStruct: Boolean) {
        if (isStruct) keywords += Keywords.CPP_STRUCT
        check(tokens.equals(i, TokenType.NAME)) { "Expected class name at ${tokens.err(i)}" }
        val name = tokens.toString(i++)
        val classScope = currPackage.getOrPut(name, ScopeType.NORMAL_CLASS)
        classScope.hasTypeParameters = true
        classScope.keywords = classScope.keywords or packKeywords()
        pushBlock(classScope) {
            readFileLevel()
        }
    }

    fun readNamespace() {
        check(tokens.equals(i, TokenType.NAME)) { "Expected namespace name at ${tokens.err(i)}" }
        val name = tokens.toString(i++)
        val scope = currPackage.getOrPut(name, ScopeType.PACKAGE)
        pushBlock(scope) {
            readFileLevel()
        }
    }

    override fun readTypeAlias() {
        TODO()
    }

    private fun readIf(): IfElseBranch {
        val condition = readExpressionCondition()
        val ifBody = readBodyOrExpression()
        val elseBody = if (consumeIf("else")) readBodyOrExpression() else null
        return IfElseBranch(condition, ifBody, elseBody)
    }

    private fun readWhile(label: String?): WhileLoop {
        val condition = readExpressionCondition()
        val body = readBodyOrExpression()
        return WhileLoop(condition, body, label)
    }

    private fun readDoWhile(label: String?): DoWhileLoop {
        val body = readBodyOrExpression()
        check(tokens.equals(i++, "while"))
        val condition = readExpressionCondition()
        return DoWhileLoop(body = body, condition = condition, label)
    }

    override fun readExpression(minPrecedence: Int): Expression {
        var expr = readPrefix()

        while (i < tokens.size) {

            val symbol = when (tokens.getType(i)) {
                TokenType.SYMBOL -> tokens.toString(i)
                TokenType.COMMA -> return expr // cannot be used
                else -> {
                    expr = tryReadPostfix(expr) ?: break
                    continue
                }
            }

            val op = cOperators[symbol] ?: run {
                expr = tryReadPostfix(expr) ?: break
                continue
            }

            if (op.precedence < minPrecedence) break

            val origin = origin(i)
            i++ // consume operator

            expr = when (symbol) {

                "&&", "||" -> {
                    val tmpName = currPackage.generateName("shortcut", origin)
                    val right = pushScope(tmpName, ScopeType.METHOD_BODY) { readRHS(op) }
                    if (symbol == "&&") shortcutExpressionI(expr, ShortcutOperator.AND, right, currPackage, origin)
                    else shortcutExpressionI(expr, ShortcutOperator.OR, right, currPackage, origin)
                }

                "?:" -> {
                    val condition = expr
                    val ifTrue = readExpression()
                    check(tokens.equals(i++, ":"))
                    val ifFalse = readExpression()
                    IfElseBranch(condition, ifTrue, ifFalse)
                }

                "." -> handleDot(expr, origin)
                "->" -> handleArrow(expr, origin)
                "::" -> handleScopeResolution(expr, origin)

                "=" -> AssignmentExpression(expr, readExpression())

                "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=" -> {
                    AssignIfMutableExpr(
                        expr, symbol,
                        shouldBeResolvable, shouldBeResolvable,
                        readExpression()
                    )
                }

                else -> {
                    val rhs = readRHS(op)
                    binaryOp(currPackage, expr, symbol, rhs, origin)
                }
            }
        }

        return expr
    }

    private fun looksLikeCast(): Boolean {
        TODO()
    }

    fun readPrefix(): Expression {
        val origin = origin(i)

        val label: String? = null
        return when {
            tokens.equals(i, "(") && looksLikeCast() -> {
                consume(TokenType.OPEN_CALL)
                val type = readTypeNotNull(null, true)
                consume(TokenType.CLOSE_CALL)
                val expr = readPrefix()
                createCastExpression(expr, currPackage, origin, type) { ifFalseScope ->
                    val debugInfoExpr = StringExpression(expr.toString(), ifFalseScope, origin)
                    val debugInfoParam = NamedParameter(null, debugInfoExpr)
                    CallExpression(
                        UnresolvedFieldExpression("throwNPE", shouldBeResolvable, ifFalseScope, origin),
                        emptyList(), listOf(debugInfoParam), origin
                    )
                }
            }

            consumeIf("sizeof") -> {
                val value = if (consumeIf("(")) {
                    val type = readTypeNotNull(null, true)
                    consume(TokenType.CLOSE_CALL)
                    GetClassFromTypeExpression(type, currPackage, origin)
                } else readPrefix()
                NamedCallExpression(
                    value, "sizeof", shouldBeResolvable,
                    currPackage, origin
                )
            }

            consumeIf("++") ->
                createPrefixExpression(InplaceModifyType.INCREMENT, origin, readPrefix())
            consumeIf("--") ->
                createPrefixExpression(InplaceModifyType.DECREMENT, origin, readPrefix())

            consumeIf("*") -> NamedCallExpression(readPrefix(), "deref", nameAsImport("deref"), currPackage, origin)
            consumeIf("&") -> NamedCallExpression(readPrefix(), "addr", nameAsImport("deref"), currPackage, origin)
            consumeIf("!") -> readPrefix().not()

            tokens.equals(i, TokenType.NUMBER) ->
                NumberExpression(tokens.toString(i++), currPackage, origin)

            tokens.equals(i, TokenType.STRING) ->
                StringExpression(tokens.toString(i++), currPackage, origin)

            consumeIf("return") -> readReturn(label)

            consumeIf("if") -> readIf()
            consumeIf("do") -> readDoWhile(label)
            consumeIf("while") -> readWhile(label)
            consumeIf("switch") -> readSwitch(label)
            consumeIf("continue") -> {
                val label = resolveBreakLabel(readLabel())
                ContinueExpression(label, currPackage, origin)
            }
            consumeIf("break") -> {
                val label = resolveBreakLabel(readLabel())
                BreakExpression(label, currPackage, origin)
            }

            // todo try to resolve field immediately
            tokens.equals(i, TokenType.NAME) -> {
                val name = tokens.toString(i++)
                UnresolvedFieldExpression(name, nameAsImport(name), currPackage, origin)
            }

            consumeIf("(") -> {
                val expr = readExpression()
                consume(TokenType.CLOSE_CALL)
                expr
            }

            else -> throw IllegalStateException("Unexpected token at ${tokens.err(i)}")
        }
    }

    private fun readLabel(): String? {
        if (!consumeIf(";")) {
            val label = tokens.toString(i++)
            consume(";")
            return label
        } else return null
    }

    private fun readReturn(label: String?): ReturnExpression {
        val origin = origin(i) // skip return
        if (LOGGER.isDebugEnabled) LOGGER.debug("reading return")
        val expr = if (i < tokens.size && tokens.isSameLine(i - 1, i) &&
            !tokens.equals(i, ",", ";")
        ) {
            val value = readExpression()
            if (LOGGER.isDebugEnabled) LOGGER.debug("  with value $value")
            ReturnExpression(value, label, currPackage, origin)
        } else {
            if (LOGGER.isDebugEnabled) LOGGER.debug("  without value")
            ReturnExpression(unitInstance, label, currPackage, origin)
        }
        consume(";")
        return expr
    }

    private fun tryReadPostfix(expr: Expression): Expression? {
        return when {
            // todo how about <>()?
            tokens.equals(i, "(") -> {
                val origin = origin(i)
                val valueParameters = pushCall { readValueParameters() }
                CallExpression(expr, null, valueParameters, origin)
            }
            tokens.equals(i, "[") -> {
                val origin = origin(i)
                val params = pushArray { readValueParameters() }
                NamedCallExpression(expr, "get", nameAsImport("get"), null, params, expr.scope, origin)
            }
            tokens.equals(i, "++") -> {
                val origin = origin(i++)
                createPostfixExpression(expr, InplaceModifyType.INCREMENT, origin)
            }
            tokens.equals(i, "--") -> {
                val origin = origin(i++)
                createPostfixExpression(expr, InplaceModifyType.DECREMENT, origin)
            }
            else -> null
        }
    }

    private fun handleDot(expr: Expression, origin: Int): Expression {
        val name = tokens.toString(i++)
        val rhs = UnresolvedFieldExpression(name, nameAsImport(name), expr.scope, origin)
        return DotExpression(expr, null, rhs, expr.scope, origin)
    }

    private fun handleArrow(expr: Expression, origin: Int): Expression {
        val deref = NamedCallExpression(expr, "deref", expr.scope, origin)
        return handleDot(deref, origin)
    }

    private fun handleScopeResolution(expr: Expression, origin: Int): Expression {
        val name = tokens.toString(i++)
        return GetMethodFromTypeExpression(
            (expr as UnresolvedFieldExpression).scope,
            name, currPackage, origin
        )
    }

    fun readBodyOrExpression(): Expression {
        return if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
            pushBlock(ScopeType.METHOD_BODY, null) {
                readMethodBody()
            }
        } else {
            readExprInNewScope()
        }
    }

    private fun readExprInNewScope(): Expression {
        val origin = origin(i)
        val tmpName = currPackage.generateName("expr", origin)
        return pushScope(tmpName, ScopeType.METHOD_BODY) {
            readExpression()
        }
    }

    override fun readMethodBody(): ExpressionList {
        val originalScope = currPackage
        val origin = origin(i)
        val result = ArrayList<Expression>()
        if (LOGGER.isDebugEnabled) LOGGER.debug("reading method body[$i], ${tokens.err(i)}")
        if (debug) tokens.printTokensInBlocks(i)
        while (i < tokens.size) {
            val oldSize = result.size
            val oldNumFields = currPackage.fields.size
            when {
                tokens.equals(i, TokenType.CLOSE_BLOCK) ->
                    throw IllegalStateException("} in the middle at ${tokens.err(i)}")
                else -> {
                    // todo read declaration or return or throw or try-catch
                    result.add(readExpression())
                    if (LOGGER.isDebugEnabled) LOGGER.debug("block += ${result.last()}")
                }
            }

            // todo check whether this works correctly
            // if expression contains assignment of any kind, or a check-call
            //  we must create a new sub-scope,
            //  because the types of our fields may have changed
            if ((result.size > oldSize && result.last().splitsScope() && i < tokens.size) ||
                currPackage.fields.size > oldNumFields
            ) {

                val newFields = currPackage.fields.subList(oldNumFields, currPackage.fields.size)
                val newScope = currPackage.generate("split", ScopeType.METHOD_BODY)
                for (field in newFields.reversed()) {
                    field.moveToScope(newScope)
                }
                currPackage = newScope

                // read remainder, and place it in the subscope
                val remainder = readMethodBody()
                // move oldSize until result into new subscope, but keep Expression.scope the same!
                val remainderList = remainder.list as ArrayList<Expression>
                for (i in oldSize until result.size) {
                    remainderList.add(0, result[i])
                }
                result.subList(oldSize, result.size).clear()
                result.add(remainder)
                // else we can skip adding it, I think
            }
        }
        val code = ExpressionList(result, originalScope, origin)
        originalScope.code.add(code)
        currPackage = originalScope // restore scope
        return code
    }

}
