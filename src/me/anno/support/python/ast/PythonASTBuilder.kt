package me.anno.support.python.ast

import me.anno.langserver.VSCodeModifier
import me.anno.langserver.VSCodeType
import me.anno.support.Language
import me.anno.support.java.ast.JavaASTBuilder
import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.ast.rich.controlflow.*
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.componentNames
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.ast.rich.expression.unresolved.*
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.ast.rich.parameter.*
import me.anno.zauber.ast.rich.parser.Associativity
import me.anno.zauber.ast.rich.parser.Operator
import me.anno.zauber.ast.rich.parser.ZauberASTBuilder.Companion.unitInstance
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.types.Import
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType

class PythonASTBuilder(tokens: TokenList, root: Scope) :
    JavaASTBuilder(tokens, root, true, Language.PYTHON) {

    companion object {
        val pythonInstanceType by threadLocal { Types.getType("PyObject") }
        val noneType by threadLocal { Types.getType("PyNull") }

        val extraOperators = mapOf(
            ":=" to Operator(":=", 1, Associativity.RIGHT),
            "is" to Operator("is", 10, Associativity.LEFT),
        )
    }

    override fun readTypePath(selfType: Type?): Type? {
        return if (consumeIf("None")) noneType
        else super.readTypePath(selfType)
    }

    override fun readFileLevel() {
        while (i < tokens.size) {
            when {
                consumeIf("import") -> readAndApplyImport()
                consumeIf("from") -> readFromImport()
                consumeIf("class") -> readClass(ScopeType.NORMAL_CLASS)

                consumeIf("continue") -> {
                    val label = resolveJumpLabel(null)
                    pushExpression(ContinueExpression(label, currPackage, origin(i - 1)))
                }
                consumeIf("break") -> {
                    val label = resolveJumpLabel(null)
                    pushExpression(BreakExpression(label, currPackage, origin(i - 1)))
                }

                tokens.equals(i, "async") && tokens.equals(i + 1, "def") -> {
                    // todo async flag? does it make a difference in python for our execution model?
                    consume("async")
                    readMethod()
                }

                tokens.equals(i, TokenType.NAME, TokenType.KEYWORD) &&
                        (tokens.equals(i + 1, TokenType.OPEN_CALL) || tokens.equals(i + 1, ".")) -> {
                    pushExpression(readExpression())
                }

                consumeIf("return") -> {
                    val origin = origin(i - 1)
                    val value = if (tokens.isSameLine(i - 1, i)) {
                        val expr = readExpression()
                        if (consumeIf(",")) {
                            val list = ArrayList<Expression>()
                            list.add(expr)
                            do {
                                list.add(readExpression())
                            } while (consumeIf(","))
                            namedCall1("tupleOf", list.map { NamedParameter(it) }, origin)
                        } else {
                            expr
                        }
                    } else unitInstance
                    pushExpression(ReturnExpression(value, null, currPackage, origin))
                }

                consumeIf("raise") -> {
                    val origin = origin(i - 1)
                    pushExpression(ThrowExpression(readExpression(), currPackage, origin))
                }

                tokens.equals(i, TokenType.NAME, TokenType.KEYWORD) &&
                        tokens.equals(i + 1, "=", ",", "+=", "-=", "*=", "/=") -> readAssignment()

                tokens.equals(i, TokenType.NAME, TokenType.KEYWORD) &&
                        tokens.equals(i + 1, "[") -> pushExpression(readExpression())

                tokens.equals(i, "await") -> {
                    i++
                    // todo wait for it??
                    pushExpression(readExpression())
                }

                consumeIf("yield") -> {
                    val origin = origin(i - 1)
                    val value = readExpression()
                    pushExpression(YieldExpression(value, currPackage, origin))
                }

                tokens.equals(i, "for") && tokens.equals(i + 1, TokenType.NAME, TokenType.KEYWORD) &&
                        tokens.equals(i + 2, "in", ",") -> pushExpression(readForLoop())

                tokens.equals(i, "def") &&
                        tokens.equals(i + 1, TokenType.NAME, TokenType.KEYWORD) -> readMethod()

                consumeIf("if") -> pushExpression(readIfBranch())
                consumeIf("while") -> pushExpression(readWhileLoop(null))
                consumeIf("with") -> pushExpression(readWith())
                consumeIf("try") -> pushExpression(readTryCatch())

                tokens.equals(i, "async") && tokens.equals(i + 1, "with") -> {
                    // todo handle this correctly somehow...
                    i += 2
                    pushExpression(readWith())
                }

                consumeIf("pass") -> {} // end of block

                consumeIf("@") -> annotations.add(readAnnotation())

                consumeIf("global") -> readGlobalVariables()

                tokens.equals(i, TokenType.STRING) && !tokens.isSameLine(i, i + 1) -> i++ // skip documentation

                else -> throw NotImplementedError("Unexpected token at ${tokens.err(i)}")
            }
        }
    }

    fun getGlobalScope(): Scope {
        var scope = currPackage
        while (!scope.isPackage()) {
            scope = scope.parent
                ?: return root
        }
        return scope
    }

    fun readGlobalVariables() {
        val scope = getGlobalScope()
        do {
            val origin = origin(i)
            val name = consumeName(VSCodeType.VARIABLE, VSCodeModifier.DECLARATION.flag)
            scope.addField(
                null, false, isMutable = true, null,
                name, pythonInstanceType, null, Flags.NONE, origin
            )
        } while (consumeIf(","))
    }

    override fun readExpression(minPrecedence: Int): Expression {
        // don't search for semicolon, those are rare anyway
        return readExpressionImpl(minPrecedence)
    }

    fun readFromImport() {
        // from a.b.c import x
        val (parentImport, nextI) = tokens.readImport(i)
        markNamespace(nextI)
        i = nextI

        consume("import")
        do {
            val name = consumeName(VSCodeType.VARIABLE, 0)
            val childImport = Import(parentImport.path.getOrPut(name, null), allChildren = false, name)
            applyImport(childImport)
        } while (consumeIf(","))
    }

    fun readMultiAssignment(name: String) {
        // todo we could support this in Zauber :3
        //  like this, we don't even need an extra keyword

        val names = ArrayList<String>()
        names.add(name)

        var lastIsMulti = false
        while (consumeIf(",")) {
            if (consumeIf("*")) lastIsMulti = true
            val name = consumeName(VSCodeType.VARIABLE, 0)
            names.add(name)
            if (lastIsMulti) break
        }

        // todo check that a swap works like this...

        val origin = origin(i)
        consume("=")
        val values = ArrayList<Expression>()
        var hasValueComma = false
        for (j in names.indices) {
            if (j > 0) {
                if (consumeIf(",")) hasValueComma = true
                else break
            }

            values.add(readExpression())
        }

        if (hasValueComma) {
            check(values.size == names.size) {
                "Expected same number of names and values, " +
                        "got ${values.size} values " +
                        "for ${names.size} names " +
                        "at ${tokens.err(i)}"
            }
            for (j in names.indices) {
                val name = names[j]
                val nameExpr = UnresolvedFieldExpression(name, nameAsImport(name), currPackage, origin)
                pushExpression(AssignmentExpression(nameExpr, values[j]))
            }
        } else {

            // use componentI instead...
            check(values.size == 1)

            val tmpField = currPackage.createImmutableField(values[0])
            val tmpFieldExpr = FieldExpression(tmpField, currPackage, origin)

            for (i in names.indices) {
                val name = names[i]
                val nameExpr = UnresolvedFieldExpression(name, nameAsImport(name), currPackage, origin)
                val methodName = if (i == names.lastIndex && lastIsMulti) "allAfter$i" else componentNames[i]
                val valueI = NamedCallExpression(tmpFieldExpr, methodName, currPackage, origin)
                pushExpression(AssignmentExpression(nameExpr, valueI))
            }
        }
    }

    fun readAssignment() {
        val name = consumeName(VSCodeType.VARIABLE, 0)
        when {
            tokens.equals(i, ",") -> readMultiAssignment(name)
            consumeIf("=") -> {
                val origin = origin(i - 1)
                val value = readExpression()
                val nameExpr = UnresolvedFieldExpression(name, nameAsImport(name), currPackage, origin)
                pushExpression(AssignmentExpression(nameExpr, value))
            }
            else -> {
                val origin = origin(i)
                val symbol = when (tokens.toString(i++)) {
                    "+=" -> "plusAssign"
                    "-=" -> "minusAssign"
                    "*=" -> "timesAssign"
                    "/=" -> "divAssign"
                    "%=" -> "remAssign"
                    else -> throw NotImplementedError("Implement ${tokens.err(i - 1)}")
                }
                val value0 = readExpression()
                val nameExpr = UnresolvedFieldExpression(name, nameAsImport(name), currPackage, origin)
                val value1 = namedCall1(
                    symbol, listOf(
                        NamedParameter(nameExpr),
                        NamedParameter(value0)
                    ), origin
                )
                pushExpression(AssignmentExpression(nameExpr, value1))
            }
        }
    }

    fun pushExpression(expr: Expression) {
        currPackage.getOrCreatePrimaryConstructorScope()
            .code.add(expr)
    }

    fun <R> pushPythonBlock(scopeType: ScopeType, prefix: String, readImpl: (Scope) -> R): R {
        return pushBlockLike({
            if (tokens.equals(i, TokenType.INDENT)) {
                // recursive, any depth
                pushPythonBlock(scopeType, prefix, readImpl)
            } else {
                // found content
                pushScope(scopeType, prefix) { scope ->
                    readImpl(scope)
                }
            }
        }, TokenType.INDENT, TokenType.DEDENT)
    }

    fun readForLoop(): Expression {
        val origin = origin(i)
        consume("for")

        val variableNames = ArrayList<String>()
        var hasComma = false
        while (variableNames.isEmpty() || !tokens.equals(i, "in")) {
            val name = consumeName(VSCodeType.FUNCTION, 0)
            variableNames.add(name)
            if (consumeIf(",")) hasComma = true
            else break
        }

        consume("in")
        val iterable = readExpression()
        consume(":")

        lateinit var fields: List<Field>
        val body = pushPythonBlock(ScopeType.METHOD_BODY, "for") { scope ->
            scope.jumpLabel = ""

            fields = variableNames.map { name ->
                scope.addField(
                    null, false, false, null,
                    name, pythonInstanceType, null, 0, origin(i)
                )
            }

            readMethodBody()
        }

        val elseBranch = if (consumeIf("else")) {
            consume(":")
            pushPythonBlock(ScopeType.METHOD_BODY, "else") {
                readMethodBody()
            }
        } else null

        val label = null
        return if (hasComma) {
            val scope = currPackage
            val fullName = scope.generateName("destruct", origin)
            val fullVariable = scope.addField(
                null, false, isMutable = false, null,
                fullName, null, null,
                Flags.NONE, origin
            )
            val fullExpr = FieldExpression(fullVariable, scope, origin)
            val newBody = ExpressionList(
                List(variableNames.size) { index ->
                    val newValue = NamedCallExpression(
                        fullExpr, componentNames[index], emptyList(),
                        scope, origin
                    )
                    val field = fields[index]
                    val variableName = FieldExpression(field, field.ownerScope, origin)
                    AssignmentExpression(variableName, newValue)
                } + body, scope, origin
            )
            createIteratorForLoop(fullVariable, iterable, newBody, label, elseBranch)
        } else {
            createIteratorForLoop(fields[0], iterable, body, label, elseBranch)
        }
    }

    override fun readPrefix(): Expression {
        return when {
            tokens.equals(i, TokenType.OPEN_CALL) -> {
                // tuple
                val origin = origin(i)
                val elements = ArrayList<Expression>()
                var hadComma = false
                pushCall {
                    while (i < tokens.size) {
                        skipIndentsAndDedents()
                        if (i >= tokens.size) break

                        elements.add(readExpression())
                        skipIndentsAndDedents()

                        if (consumeIf(",")) hadComma = true
                        else break
                    }
                }
                if (hadComma || elements.isEmpty()) {
                    namedCall("createTuple", elements, origin)
                } else {
                    elements[0]
                }
            }

            // todo do we need to do anything special?
            consumeIf("await") -> readPrefix()

            tokens.equals(i, TokenType.OPEN_ARRAY) -> {
                // array
                val origin = origin(i)
                val elements = pushArray { readValueParametersBody() }
                namedCall1("createArray", elements, origin)
            }

            tokens.equals(i, TokenType.OPEN_BLOCK) -> {
                // array
                val origin = origin(i)
                val elements = pushBlock { readValueParametersBody() }
                namedCall1("createDict", elements, origin)
            }

            isFString(i) -> readFString()

            consumeIf("True") -> SpecialValueExpression(SpecialValue.TRUE, currPackage, origin(i - 1))
            consumeIf("False") -> SpecialValueExpression(SpecialValue.FALSE, currPackage, origin(i - 1))
            consumeIf("None") -> SpecialValueExpression(SpecialValue.NULL, currPackage, origin(i - 1))

            tokens.equals(i, "and", "or", "not") -> {
                val origin = origin(i)
                val name = tokens.toString(i++)
                namedCall(name, readExpression(), origin)
            }

            else -> super.readPrefix()
        }
    }

    fun isFString(i: Int): Boolean {
        return tokens.equals(i, "f") && tokens.equals(i, TokenType.KEYWORD) &&
                tokens.equals(i + 1, TokenType.OPEN_CALL)
    }

    override fun readValueParametersBody(): ArrayList<NamedParameter> {
        val elements = ArrayList<NamedParameter>()
        // println("params-end: ${tokens.size}, ${tokens.err(tokens.size)}")
        while (i < tokens.size) {
            skipIndentsAndDedents()
            if (i >= tokens.size) break

            val isVarDictStar = consumeIf("**")

            // println("reading param at ${tokens.err(i)}")

            var expr = readExpression()
            if (isVarDictStar) expr = ArrayToVarDictStar(expr)

            elements.add(NamedParameter(expr))
            skipIndentsAndDedents()

            readComma()
        }
        return elements
    }

    fun skipIndentsAndDedents() {
        while (tokens.equals(i, TokenType.INDENT, TokenType.DEDENT)) i++
    }

    fun readFString(): Expression {
        val origin = origin(i)

        consume("f", TokenType.KEYWORD)

        val parts = ArrayList<Expression>()
        pushCall {
            while (i < tokens.size) {
                when {
                    tokens.equals(i, TokenType.STRING) -> {
                        val origin = origin(i)
                        val value = tokens.toString(i++)
                        parts.add(StringExpression(value, currPackage, origin))
                    }
                    consumeIf(TokenType.APPEND_STRING) -> {}
                    tokens.equals(i, TokenType.OPEN_CALL) -> {
                        var format = ""
                        val expr = pushCall {
                            val expr = readExpression()
                            if (consumeIf("!")) {
                                while (i < tokens.size) {
                                    format += tokens.toString(i++)
                                }
                            }
                            expr
                        }
                        val call = namedCall1(
                            "strFormat", if (format == "") {
                                listOf(NamedParameter(expr))
                            } else {
                                val formatExpr = StringExpression(format, currPackage, origin)
                                listOf(NamedParameter(expr), NamedParameter(formatExpr))
                            }, origin
                        )
                        parts.add(call)
                    }
                    else -> throw NotImplementedError("Unexpected token in f-string at ${tokens.err(i)}")
                }
            }
        }

        // build concatenation chain
        return buildFString(parts, origin)
    }

    private fun buildFString(parts: List<Expression>, origin: Long): Expression {
        if (parts.isEmpty()) return StringExpression("", currPackage, origin)

        var result = parts[0]
        for (i in 1 until parts.size) {
            result = namedCall("strConcat", listOf(result, parts[i]), origin)
        }

        return result
    }

    override fun tryReadPostfix(expr: Expression): Expression? {
        return when {
            // string literals can be concatenated without plus symbol
            isFString(i) || tokens.equals(i, TokenType.STRING) ||
                    tokens.equals(i, TokenType.OPEN_CALL) -> {
                val origin = origin(i)
                val other = readExpression()
                namedCall("strConcat", listOf(expr, other), origin)
            }
            tokens.equals(i, ":") && !tokens.equals(i + 1, TokenType.INDENT) -> {
                consume(":")
                // todo use the proper names...
                // todo it can start with a colon, too, I believe...
                val origin = origin(i)
                if (tokens.equals(i, ",", "]", ")", "}")) {
                    return namedCall("rangeToUndef", expr, origin)
                }
                val other = readExpression()
                namedCall("rangeTo", listOf(expr, other), origin)
            }
            tokens.equals(i, "if") -> {
                val i0 = i
                consume("if")
                // todo we would need to read the if in an inner scope, not the condition...
                val condition = pushScope(ScopeType.METHOD_BODY, "if") {
                    readExpression()
                }
                if (consumeIf(":")) {
                    // not an inline-if -> skip
                    i = i0
                    return super.tryReadPostfix(expr)
                }

                consume("else")
                val other = pushScope(ScopeType.METHOD_BODY, "else") {
                    readExpression()
                }
                IfElseBranch(condition, expr, other)
            }
            tokens.equals(i, "for") &&
                    tokens.equals(i + 1, TokenType.NAME, TokenType.KEYWORD) &&
                    tokens.equals(i + 2, "in") -> {

                val i0 = i
                val origin = origin(i)
                consume("for")

                val nameOrigin = origin(i)
                val name = consumeName(VSCodeType.PROPERTY, VSCodeModifier.DECLARATION.flag)
                consume("in")

                val iterable = readExpression()
                if (consumeIf(":")) {
                    // not an inline if -> reset
                    i = i0
                    return super.tryReadPostfix(expr)
                }

                val field = currPackage.addField(
                    null, false, isMutable = false, null,
                    name, pythonInstanceType, null, 0, nameOrigin
                )

                val fieldExpr = FieldExpression(field, field.scope, field.origin)

                val iterator = iterableToIterator(iterable)
                val iteratorField = currPackage.createImmutableField(iterator)
                val iteratorFieldExpr = FieldExpression(iteratorField, currPackage, origin)

                val arrayInit = namedCall("createArray", emptyList(), origin)
                val arrayField = currPackage.createImmutableField(arrayInit)
                val arrayFieldExpr = FieldExpression(arrayField, currPackage, origin)

                val condition = iteratorToHasNext(iteratorFieldExpr, currPackage, origin)
                val next = iteratorToNext(iteratorFieldExpr, currPackage, origin)
                val body = ExpressionList(
                    listOf(
                        AssignmentExpression(fieldExpr, next),
                        expr // todo field needs to be defined for expr, which comes first ...
                    ),
                    currPackage, origin,
                )

                ExpressionList(
                    listOf(
                        AssignmentExpression(arrayFieldExpr, arrayInit),
                        AssignmentExpression(iteratorFieldExpr, iterator),
                        WhileLoop(condition, body, null, null),
                        arrayFieldExpr
                    ), currPackage, origin
                )
            }
            else -> super.tryReadPostfix(expr)
        }
    }

    private fun namedCall(name: String, expr: Expression, origin: Long): Expression {
        return namedCall(name, listOf(expr), origin)
    }

    private fun namedCall(name: String, expr: List<Expression>, origin: Long): Expression {
        return namedCall1(name, expr.map { NamedParameter(null, it) }, origin)
    }

    private fun namedCall1(name: String, expr: List<NamedParameter>, origin: Long): Expression {
        val nameExpr = UnresolvedFieldExpression(name, emptyList(), currPackage, origin)
        return CallExpression(nameExpr, emptyList(), expr, origin)
    }

    fun readMethod() {
        val origin = origin(i)
        consume("def")
        val name = consumeName(VSCodeType.METHOD, 0)
        val valueParameters = pushCall {
            readParameterDeclarations(currPackage.typeWithArgs, emptyList(), ParameterType.VALUE_PARAMETER)
        }
        val returnType = if (consumeIf("->")) {
            readTypeNotNull(currPackage.typeWithArgs, true)
        } else null
        consume(":")
        pushPythonBlock(ScopeType.METHOD, name) { methodScope ->
            val bodyExpr = readMethodBody()
            methodScope.selfAsMethod = Method(
                null, false, name, emptyList(), valueParameters,
                methodScope, returnType, emptyList(), bodyExpr, 0, origin
            )
        }
    }

    override fun readMethodBody(): ExpressionList {
        val bodyOrigin = origin(i)
        readFileLevel()

        val methodScope = currPackage
        val body = methodScope.getOrCreatePrimaryConstructorScope().code
        return ExpressionList(body, methodScope, bodyOrigin)
    }

    override fun readParameterDeclarations(
        selfType: Type?,
        extra: List<Parameter>,
        parameterType: ParameterType
    ): List<Parameter> {
        val parameters = ArrayList<Parameter>(extra)
        var mustBeNamed = false
        loop@ while (i < tokens.size) {

            val isVarDict = consumeIf("*")
            if (consumeIf(",")) {
                mustBeNamed = true
                continue
            }

            if (consumeIf("**")) {
                // todo this is a special value: any extra parameters are pushed into a dictionary
            }

            val isVal = false
            val origin = origin(i)
            val name = consumeName(VSCodeType.PARAMETER, 0)

            var type =
                if (consumeIf(":")) readTypeNotNull(selfType, true)
                else pythonInstanceType

            if (isVarDict) {
                // todo not really an array, but a dictionary
                type = Types.Array.withTypeParameter(type)
            }

            val defaultValue =
                if (consumeIf("=")) readExpression()
                else null

            // println("Found $name: $type = $initialValue at ${resolveOrigin(i)}")

            val keywords = packFlags()
            val parameter = Parameter(
                parameters.size,
                if (isVal) ParameterMutability.VAL else ParameterMutability.VAR,
                if (isVarDict) ParameterExpansion.VARDICT else ParameterExpansion.NONE,
                parameterType, name, type, defaultValue, currPackage, origin
            )
            parameter.getOrCreateField(selfType, keywords)
            parameters.add(parameter)

            readComma()
        }
        return parameters
    }

    fun readWith(): TryCatchBlock {
        val origin = origin(i - 1)
        val end = tokens.findBlockEnd(i - 1, "with", "as")
        val value = push(end) { readExpression() }
        val name = consumeName(VSCodeType.PROPERTY, VSCodeModifier.DECLARATION.flag)
        consume(":")
        lateinit var fieldExpr: FieldExpression
        val body = pushPythonBlock(ScopeType.METHOD_BODY, "with") { scope ->
            val field = scope.addField(
                null, false, false, null,
                name, pythonInstanceType, value, 0, origin
            )
            fieldExpr = FieldExpression(field, scope, origin)
            pushExpression(AssignmentExpression(fieldExpr, value))
            readMethodBody()
        }
        return TryCatchBlock(
            body, emptyList(),
            NamedCallExpression(fieldExpr, "close", currPackage, origin),
            currPackage, origin
        )
    }

    override fun readIfBranch(): IfElseBranch {
        val condition = readExpression()
        consume(":")
        val body = pushPythonBlock(ScopeType.METHOD_BODY, "if") {
            readMethodBody()
        }
        val elseBranch = when {
            consumeIf("else") -> {
                consume(":")
                pushPythonBlock(ScopeType.METHOD_BODY, "else") {
                    readMethodBody()
                }
            }
            consumeIf("elif") -> {
                pushScope(ScopeType.METHOD_BODY, "elif") {
                    readIfBranch()
                }
            }
            else -> null
        }
        return IfElseBranch(condition, body, elseBranch)
    }

    override fun readWhileLoop(label: String?): WhileLoop {
        val condition = readExpression()
        consume(":")
        val body = pushPythonBlock(ScopeType.METHOD_BODY, "while") { bodyScope ->
            bodyScope.jumpLabel = label ?: ""
            readMethodBody()
        }
        val elseBranch = when {
            consumeIf("else") -> {
                consume(":")
                pushPythonBlock(ScopeType.METHOD_BODY, "else") {
                    readMethodBody()
                }
            }
            else -> null
        }
        return WhileLoop(condition, body, null, elseBranch)
    }

    override fun readTryCatch(finallyOverride: Expression?): Expression {
        // try with resource
        val origin = origin(i - 1)
        consume(":")

        val tryBody = pushPythonBlock(ScopeType.METHOD_BODY, "try") {
            readMethodBody()
        }

        val catches = ArrayList<Catch>()
        while (consumeIf("except")) {
            val origin = origin(i - 1)
            val typeName = if (!tokens.equals(i, ":")) {
                val end0 = tokens.findToken(i, ":")
                val end1 = if (tokens.equals(end0 - 2, "as")) end0 - 2 else end0
                push(end1) {
                    readTypeNotNull(null, true)
                }.apply { i-- }
            } else pythonInstanceType
            val variableName = if (consumeIf("as")) {
                consumeName(VSCodeType.PARAMETER, VSCodeModifier.DECLARATION.flag)
            } else "?"
            val parameter = Parameter(0, variableName, ParameterType.VALUE_PARAMETER, typeName, currPackage, origin)
            consume(":")
            pushPythonBlock(ScopeType.METHOD_BODY, "catch") {
                val handler = readMethodBody()
                catches.add(Catch(parameter, handler, origin))
            }
        }

        val elseBlockI = if (consumeIf("else")) {
            // this executes when no error was thrown, so after the body, but outside try-catch
            consume(":")
            val initial = SpecialValueExpression(SpecialValue.FALSE, currPackage, origin)
            val noErrorField = currPackage.addField(
                null, false, isMutable = true, null,
                "__noError_${origin.toString(36)}", Types.Boolean,
                initial, Flags.NONE, origin
            )
            val elseBlock = pushPythonBlock(ScopeType.METHOD_BODY, "else") {
                readMethodBody()
            }
            Pair(elseBlock, noErrorField)
        } else null

        val finally = finallyOverride
            ?: if (consumeIf("finally")) {
                consume(":")
                pushPythonBlock(ScopeType.METHOD_BODY, "finally") {
                    readMethodBody()
                }
            } else null

        return if (elseBlockI != null) {
            val (elseBlock, noErrorField) = elseBlockI
            val fieldExpr = FieldExpression(noErrorField, currPackage, origin)
            val trueExpr = SpecialValueExpression(SpecialValue.TRUE, currPackage, origin)
            val tryBody2 = ExpressionList(
                listOf(tryBody, AssignmentExpression(fieldExpr, trueExpr)),
                currPackage, origin
            )
            ExpressionList(
                listOf(
                    AssignmentExpression(fieldExpr, noErrorField.initialValue!!),
                    TryCatchBlock(tryBody2, catches, finally, currPackage, origin),
                    IfElseBranch(fieldExpr, elseBlock, null)
                ), currPackage, origin
            )

        } else {
            TryCatchBlock(tryBody, catches, finally, currPackage, origin)
        }
    }

    override fun readClass(scopeType: ScopeType) {
        val name = consumeName(VSCodeType.CLASS, VSCodeModifier.DECLARATION.flag)
        pushScope(name, ScopeType.NORMAL_CLASS) { classScope ->
            classScope.setEmptyTypeParams()

            if (tokens.equals(i, TokenType.OPEN_CALL)) {
                pushCall {
                    readSuperCalls(classScope, true)
                }
            }

            if (consumeIf(":")) {
                pushPythonBlock(ScopeType.NORMAL_CLASS, "class") {
                    readFileLevel()
                }
            }
        }
    }

    override fun readSuperCalls(classScope: Scope, readBody: Boolean) {
        do {
            val origin = origin(i)
            val type = readTypeNotNull(null, true).resolvedName as ClassType
            classScope.superCalls.add(SuperCall(type, emptyList(), null, origin))
        } while (consumeIf(","))
    }

    override fun getOperator(symbol: String): Operator? {
        return super.getOperator(symbol) ?: extraOperators[symbol]
    }

}