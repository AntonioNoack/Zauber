package me.anno.support.python.ast

import me.anno.langserver.VSCodeModifier
import me.anno.langserver.VSCodeType
import me.anno.support.Language
import me.anno.support.java.ast.JavaASTBuilder
import me.anno.support.python.EitherOr
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
import me.anno.zauber.types.impl.arithmetic.UnionType.Companion.unionTypes

class PythonASTBuilder(tokens: TokenList, root: Scope) :
    JavaASTBuilder(tokens, root, true, Language.PYTHON) {

    companion object {
        val pythonInstanceType by threadLocal { Types.getType("PyObject") }
        val noneType by threadLocal { Types.getType("PyNull") }

        val extraOperators = mapOf(
            ":=" to Operator(":=", 1, Associativity.RIGHT),

            "in" to Operator("in", 10, Associativity.LEFT),
            "!in" to Operator("!in", 10, Associativity.LEFT),

            "//" to Operator("//", 20, Associativity.LEFT),
            "**" to Operator("**", 25, Associativity.LEFT),
        )
    }

    override fun readTypePath(selfType: Type?): Type? {
        return if (consumeIf("None")) noneType
        else super.readTypePath(selfType)
    }

    override fun readTypeParameters(selfType: Type?): List<Type>? {
        if (tokens.equals(i, TokenType.OPEN_ARRAY)) {
            val params = ArrayList<Type>()
            var isValid = true // only if contents are plausible...
            pushArray {
                while (i < tokens.size) {
                    val type = readType(selfType, true)
                    if (type != null) params.add(type)
                    else {
                        isValid = false
                        i = tokens.size
                        break
                    }
                }
            }
            return if (isValid) params else null
        }
        return null
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

                consumeIf("if") -> pushExpression(readIfBranch())
                consumeIf("while") -> pushExpression(readWhileLoop(null))
                consumeIf("with") -> pushExpression(readWith())
                consumeIf("try") -> pushExpression(readTryCatch())
                consumeIf("match") -> pushExpression(readMatch())
                consumeIf("pass") -> {} // end of block
                consumeIf("@") -> annotations.add(readAnnotation())
                consumeIf("global") -> readGlobalVariables()
                consumeIf("nonlocal") -> readNonLocalVariables()
                consumeIf("assert") -> readAssert()

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
                            namedCall("tupleOf", list, origin)
                        } else {
                            expr
                        }
                    } else unitInstance
                    pushExpression(ReturnExpression(value, null, currPackage, origin))
                }

                consumeIf("raise") -> {
                    val origin = origin(i - 1)
                    val body = if (tokens.isSameLine(i - 1, i)) {
                        readExpression()
                    } else unitInstance // todo we're inside a catch-block, use its field, (even if unnamed)
                    pushExpression(ThrowExpression(body, currPackage, origin))
                    if (consumeIf("from")) {
                        consume("None")
                        // todo this somehow suppresses "exception chaining":
                        //  looks like by default, Python adds 'e' to new exceptions in catch-blocks,
                        //  but in this case, no 'e' shall be passed
                    }
                }

                tokens.equals(i, TokenType.NAME, TokenType.KEYWORD) &&
                        tokens.equals(i + 1, "=", ",", "+=", "-=", "*=", "/=") -> readAssignment(false)

                tokens.equals(i, TokenType.NAME, TokenType.KEYWORD) &&
                        tokens.equals(i + 1, ":") -> readAssignment(true)

                tokens.equals(i, TokenType.NAME, TokenType.KEYWORD) &&
                        tokens.equals(i + 1, "[") -> pushExpression(readExpression())

                consumeIf("await") -> {
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

                tokens.equals(i, "async") && tokens.equals(i + 1, "with") -> {
                    // todo handle this correctly somehow...
                    i += 2
                    pushExpression(readWith())
                }

                tokens.equals(i, TokenType.STRING) && !tokens.isSameLine(i, i + 1) -> i++ // skip documentation

                else -> throw NotImplementedError("Unexpected token at ${tokens.err(i)}")
            }
        }
    }

    override fun readOperatorSymbol(): Pair<String, Int>? {

        if (tokens.equals(i, "is")) {
            return if (tokens.equals(i + 1, "not")) "!is" to 2
            else "is" to 1
        }

        if (tokens.equals(i, "not") && tokens.equals(i + 1, "in")) {
            return "!in" to 2
        }

        if (tokens.equals(i, "in")) {
            return "in" to 1
        }

        if (tokens.equals(i, "as")) {
            return null
        }

        return super.readOperatorSymbol()
    }

    fun readAssert() {
        val origin = origin(i - 1)
        val condition = readExpression()
        val message = if (consumeIf(",")) {
            readExpression()
        } else {
            StringExpression("Assertion failed", currPackage, origin)
        }
        pushExpression(namedCall("assert", listOf(condition, message), origin))
    }

    override fun readAndApplyImport() {
        do {
            super.readAndApplyImport()
        } while (consumeIf(","))
    }

    fun readLambda(): Expression {
        return pushScope(ScopeType.LAMBDA, "lambda") { lambdaScope ->
            // first read method parameters until :,
            val selfType = currPackage.typeWithArgs
            val end = tokens.findToken(i, ":")
            val valueParams = push(end) {
                readParameterDeclarations(selfType, emptyList(), ParameterType.VALUE_PARAMETER)
            }
            // then read the body immediately after (not indented as usual)
            val body = readExpression()
            val variables = valueParams.map { param ->
                LambdaVariable(param.type, param.getOrCreateField(null, Flags.NONE), param.origin)
            }
            LambdaExpression(variables, lambdaScope, body)
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

    fun findMethodScope(scope0: Scope): Scope {
        var scope = scope0
        while (!scope.isMethod()) {
            scope = scope.parent!!
        }
        return scope
    }


    fun getNonLocalScope(): Scope {
        val method = findMethodScope(currPackage)
        return findMethodScope(method.parent!!)
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

    fun readNonLocalVariables() {
        val scope = getNonLocalScope()
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
        if (consumeIf("*")) {
            val childImport = Import(parentImport.path, allChildren = true, parentImport.name)
            return applyImport(childImport)
        }

        do {
            val childName = consumeName(VSCodeType.VARIABLE, 0)
            val importName = if (consumeIf("as")) {
                consumeName(VSCodeType.VARIABLE, 0)
            } else childName
            val scope = parentImport.path.getOrPut(childName, null)
            val childImport = Import(scope, allChildren = false, importName)
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

    fun readAssignment(withType: Boolean) {
        val name = consumeName(VSCodeType.VARIABLE, 0)
        val type = if (withType) { // todo use that type somehow...
            consume(":")
            readTypeNotNull(null, true)
        } else null
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
                val value1 = namedCall(symbol, listOf(nameExpr, value0), origin)
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

    fun readForNames(): Pair<List<String>, Boolean> {
        val variableNames = ArrayList<String>()
        var hasComma = false
        while (variableNames.isEmpty() || !tokens.equals(i, "in")) {
            val name = consumeName(VSCodeType.FUNCTION, 0)
            variableNames.add(name)
            if (consumeIf(",")) hasComma = true
            else break
        }
        return variableNames to hasComma
    }

    fun readForLoop(): Expression {
        val origin = origin(i)
        consume("for")
        val (variableNames, hasComma) = readForNames()
        consume("in")

        var iterable = readExpression()
        if (consumeIf(",")) {
            // Python allows tuples without parentheses in many places
            val elements = ArrayList<Expression>()
            elements.add(iterable)
            do {
                elements.add(readExpression())
            } while (consumeIf(","))
            iterable = namedCall("tupleOf", elements, origin)
        }

        consume(":")

        lateinit var fields: List<Field>
        val body = pushPythonBlock(ScopeType.METHOD_BODY, "for") { scope ->
            scope.jumpLabel = ""
            fields = createForLoopFields(variableNames, scope)
            readMethodBody()
        }

        val elseBranch = if (consumeIf("else")) {
            consume(":")
            pushPythonBlock(ScopeType.METHOD_BODY, "else") {
                readMethodBody()
            }
        } else null

        return createForLoop(
            variableNames, hasComma, fields,
            iterable, body, elseBranch, origin
        )
    }

    fun createForLoopFields(variableNames: List<String>, scope: Scope): List<Field> {
        return variableNames.map { name ->
            scope.addField(
                null, false, false, null,
                name, pythonInstanceType, null, 0, origin(i)
            )
        }
    }

    fun createForLoop(
        variableNames: List<String>, hasComma: Boolean, fields: List<Field>,
        iterable: Expression, body: Expression, elseBranch: Expression?, origin: Long,
    ): Expression {
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
                val elements = pushScope(ScopeType.METHOD_BODY, "array") {
                    pushArray { readValueParametersBody() }
                }
                namedCall1("createArray", elements, origin)
            }

            tokens.equals(i, TokenType.OPEN_BLOCK) -> {
                // dict or set
                val origin = origin(i)
                val eitherOr = pushScope(ScopeType.METHOD_BODY, "dict") {
                    pushBlock { readDictOrSet() }
                }
                eitherOr.map({ pair ->
                    val (elements, isDict) = pair
                    namedCall(if (isDict) "createDict" else "createSet", elements, origin)
                }, { expr -> expr })
            }

            isFString(i) -> readFString()

            consumeIf("True") -> SpecialValueExpression(SpecialValue.TRUE, currPackage, origin(i - 1))
            consumeIf("False") -> SpecialValueExpression(SpecialValue.FALSE, currPackage, origin(i - 1))
            consumeIf("None") -> SpecialValueExpression(SpecialValue.NULL, currPackage, origin(i - 1))

            consumeIf("not") -> {
                val origin = origin(i - 1)
                namedCall("not", readExpression(), origin)
            }

            consumeIf("lambda") -> readLambda()
            consumeIf("with") -> readWith()

            else -> super.readPrefix()
        }
    }

    fun isFString(i: Int): Boolean {
        return tokens.equals(i, "f") && tokens.equals(i, TokenType.KEYWORD) &&
                tokens.equals(i + 1, TokenType.OPEN_CALL)
    }

    fun readDictOrSet(): EitherOr<Pair<List<Expression>, Boolean>, Expression> {
        if (i >= tokens.size) return EitherOr(emptyList<Expression>() to true) /* default is dict */

        var isDict = false
        val properties = ArrayList<Expression>()
        while (i < tokens.size) {
            skipIndentsAndDedents()

            val key = readExpression()
            if (tokens.equals(i, "for")) {
                check(properties.isEmpty())
                val value = readForLoopForSet(key)
                skipIndentsAndDedents()
                return EitherOr(value, Unit)
            }

            if (consumeIf(":")) {
                val value = readExpression()
                check(properties.isEmpty() || isDict)

                if (tokens.equals(i, "for")) {
                    TODO("Read for-loop for dict: ${tokens.err(i - 1)}")
                }

                properties.add(key)
                properties.add(value)
                isDict = true
            } else {
                check(properties.isEmpty() || !isDict)
                properties.add(key)
                isDict = false
            }
            skipIndentsAndDedents()
            readComma()
            skipIndentsAndDedents()
        }
        return EitherOr(properties to isDict)
    }

    fun readForLoopForArray(inner: Expression): Expression {
        return readForLoopForAnyCollection(inner, "ArrayList")
    }

    fun readForLoopForSet(inner: Expression): Expression {
        return readForLoopForAnyCollection(inner, "HashSet")
    }

    fun readForLoopForAnyCollection(inner: Expression, typeName: String): Expression {
        val innerScope = currPackage
        val outerScope = innerScope.parent!!

        val origin = origin(i)
        consume("for")
        val (variableNames, hasComma) = readForNames()
        val fields = createForLoopFields(variableNames, outerScope)
        consume("in")
        val iterable = readExpression()
        val condition = if (consumeIf("if")) {
            readExpression()
        } else null

        val resultField = outerScope.addField(
            null, false, isMutable = false, null,
            "__for_${origin.toString(36)}", pythonInstanceType, null, Flags.NONE, origin
        )
        val resultFieldExpr = FieldExpression(resultField, outerScope, origin)
        val body0 = NamedCallExpression(resultFieldExpr, "add", inner, outerScope, origin)
        val body1 = if (condition == null) body0 else {
            IfElseBranch(condition, body0, null)
        }
        return ExpressionList(
            outerScope, origin,
            AssignmentExpression(resultFieldExpr, namedCall(typeName, emptyList(), origin)),
            createForLoop(variableNames, hasComma, fields, iterable, body1, null, origin)
        )
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
            var mustBeLast = false
            while (tokens.equals(i, "for")) {
                check(elements.isEmpty())
                mustBeLast = true
                expr = readForLoopForArray(expr)
            }
            if (isVarDictStar) expr = ArrayToVarDictStar(expr)

            elements.add(NamedParameter(expr))
            skipIndentsAndDedents()

            if (mustBeLast) break
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
                            // println("fString-Expr: $expr")
                            when {
                                consumeIf("!") -> {
                                    format = "!"
                                    while (i < tokens.size) {
                                        format += tokens.toString(i++)
                                    }
                                }
                                consumeIf(":") -> {
                                    format = ":"
                                    while (i < tokens.size) {
                                        format += tokens.toString(i++)
                                    }
                                }
                            }
                            expr
                        }
                        val call = namedCall(
                            "strFormat", if (format == "") {
                                listOf(expr)
                            } else {
                                val formatExpr = StringExpression(format, currPackage, origin)
                                listOf(expr, formatExpr)
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
            false && tokens.equals(i, ":") && !tokens.equals(i + 1, TokenType.INDENT) -> {
                consume(":")
                // todo use the proper names...
                // todo it can start with a colon, too, I believe...
                val origin = origin(i)
                if (tokens.equals(i, TokenType.COMMA, TokenType.CLOSE_BLOCK) ||
                    tokens.equals(i, TokenType.CLOSE_CALL, TokenType.CLOSE_ARRAY)
                ) {
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
                if (consumeIf("else")) {
                    val other = pushScope(ScopeType.METHOD_BODY, "else") {
                        readExpression()
                    }
                    IfElseBranch(condition, expr, other)
                } else {
                    // println("Rejecting inline if-else at ${tokens.err(i0)}")
                    // not an inline-if -> skip
                    i = i0
                    super.tryReadPostfix(expr)
                }
            }
            tokens.equals(i, TokenType.OPEN_ARRAY) -> readArrayAccess(expr)
            else -> super.tryReadPostfix(expr)
        }
    }

    fun readArrayAccess(expr: Expression): Expression {
        return pushArray {
            val origin = origin(i - 1)
            // start:stop:step
            if (consumeIf("::")) {
                val step = readExpression()
                namedCall("arrayStep", listOf(expr, step), origin)
            } else if (consumeIf(":")) {
                val stop = readExpression()
                namedCall("arrayUntilStop", listOf(expr, stop), origin)
            } else {
                val index = readExpression()
                if (consumeIf(":")) {
                    val stop = readExpression()
                    consume(":")
                    val step = readExpression()
                    namedCall("arraySub", listOf(expr, index, stop, step), origin)
                } else {
                    namedCall("arrayGet", listOf(expr, index), origin)
                }
            }
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
        val value = readExpression()
        val name = if (consumeIf("as")) {
            consumeName(VSCodeType.PROPERTY, VSCodeModifier.DECLARATION.flag)
        } else null
        consume(":")
        lateinit var fieldExpr: FieldExpression
        val body = pushPythonBlock(ScopeType.METHOD_BODY, "with") { scope ->
            if (name != null) {
                val field = scope.addField(
                    null, false, false, null,
                    name, pythonInstanceType, value, 0, origin
                )
                fieldExpr = FieldExpression(field, scope, origin)
                pushExpression(AssignmentExpression(fieldExpr, value))
            } else {
                pushExpression(value)
            }
            readMethodBody()
        }
        val finally = if (name != null) {
            NamedCallExpression(fieldExpr, "close", currPackage, origin)
        } else null
        return TryCatchBlock(body, emptyList(), finally, currPackage, origin)
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
            if (consumeIf("*")) {
                // todo parent is an exception group, and we must scan the members
                //  whether any of those is such a type
            }
            val typeName = if (!tokens.equals(i, ":")) {
                val end0 = tokens.findToken(i, ":")
                val end1 = if (tokens.equals(end0 - 2, "as")) end0 - 2 else end0
                push(end1) {
                    readThrownType()
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

    fun readThrownType(): Type {
        return if (tokens.equals(i, TokenType.OPEN_CALL)) {
            pushCall {
                val types = ArrayList<Type>()
                while (i < tokens.size) {
                    val typeI = readTypeNotNull(null, true)
                    types.add(typeI)
                    readComma()
                }
                unionTypes(types)
            }
        } else {
            readTypeNotNull(null, true)
        }
    }

    fun readMatch(): Expression {
        val origin = origin(i - 1)
        val value = readExpression()
        consume(":")
        return pushPythonBlock(ScopeType.METHOD_BODY, "match") {
            val cases = ArrayList<WhenCase>()
            while (i < tokens.size) {
                consume("case")
                pushScope(ScopeType.METHOD_BODY, "caseI") {
                    val condition = namedCall("equals", listOf(value, readExpression()), origin)
                    consume(":")
                    val body = pushPythonBlock(ScopeType.METHOD_BODY, "case") {
                        readMethodBody()
                    }
                    cases.add(WhenCase(condition, body))
                }
            }
            whenBranchToIfElseChain(cases, currPackage, origin)
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