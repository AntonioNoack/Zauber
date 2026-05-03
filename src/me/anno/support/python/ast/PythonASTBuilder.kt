package me.anno.support.python.ast

import me.anno.langserver.VSCodeModifier
import me.anno.langserver.VSCodeType
import me.anno.support.Language
import me.anno.support.java.ast.JavaASTBuilder
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.NamedParameter
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.controlflow.IfElseBranch
import me.anno.zauber.ast.rich.controlflow.TryCatchBlock
import me.anno.zauber.ast.rich.controlflow.forLoop
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.unresolved.*
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType

class PythonASTBuilder(tokens: TokenList, root: Scope) :
    JavaASTBuilder(tokens, root, true, Language.PYTHON) {

    companion object {
        val pythonInstanceType: ClassType
            get() = Types.getType("PythonInstance")
    }

    override fun readFileLevel() {
        while (i < tokens.size) {
            when {
                consumeIf("import") -> readAndApplyImport()

                tokens.equals(i, TokenType.NAME, TokenType.KEYWORD) &&
                        (tokens.equals(i + 1, TokenType.OPEN_CALL) || tokens.equals(i + 1, ".")) -> {
                    pushExpression(readExpression())
                }

                tokens.equals(i, TokenType.NAME, TokenType.KEYWORD) &&
                        tokens.equals(i + 1, "=") -> readAssignment()

                tokens.equals(i, "for") && tokens.equals(i + 1, TokenType.NAME, TokenType.KEYWORD) &&
                        tokens.equals(i + 2, "in") -> readForLoop()

                tokens.equals(i, "def") &&
                        tokens.equals(i + 1, TokenType.NAME, TokenType.KEYWORD) -> readMethod()

                tokens.equals(i, "if") -> pushExpression(readIfBranch())
                tokens.equals(i, "with") -> pushExpression(readWith())

                else -> throw NotImplementedError("Unexpected token at ${tokens.err(i)}")
            }
        }
    }

    fun readAssignment() {
        val origin = origin(i)
        val name = consumeName(VSCodeType.FUNCTION, 0)
        consume("=")
        val value = readExpression()
        val nameExpr = UnresolvedFieldExpression(name, nameAsImport(name), currPackage, origin)
        pushExpression(AssignmentExpression(nameExpr, value))
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

    fun readForLoop() {
        consume("for")
        val name = consumeName(VSCodeType.FUNCTION, 0)
        consume("in")
        val iterable = readExpression()
        consume(":")

        lateinit var field: Field
        val body = pushPythonBlock(ScopeType.METHOD_BODY, "for") { scope ->
            field = scope.addField(
                null, false, false, null,
                name, pythonInstanceType, null, 0, origin(i)
            )

            readMethodBody()
        }

        val label = null
        pushExpression(forLoop(field, iterable, body, label))
    }

    override fun readExpression(minPrecedence: Int): Expression {
        return if (tokens.equals(i, "(")) {
            // tuple
            val origin = origin(i)
            val elements = ArrayList<Expression>()
            var hadComma = false
            pushCall {
                while (i < tokens.size) {
                    while (tokens.equals(i, TokenType.INDENT, TokenType.DEDENT)) i++ // idc
                    if (i >= tokens.size) break

                    elements.add(readExpression())

                    if (consumeIf(",")) hadComma = true
                    else break
                }
            }
            if (hadComma || elements.isEmpty()) {
                // todo does this have a standardized name?
                namedCall("createTuple", elements, origin)
            } else {
                elements[0]
            }
        } else super.readExpression(minPrecedence)
    }

    override fun tryReadPostfix(expr: Expression): Expression? {
        if (tokens.equals(i, ":") && !tokens.equals(i + 1, TokenType.INDENT)) {
            consume(":")
            // todo use the proper names...
            // todo it can start with a colon, too, I believe...
            val origin = origin(i)
            if (tokens.equals(i, ",", "]", ")", "}")) {
                return namedCall("rangeToUndef", expr, origin)
            }
            val other = readExpression()
            return namedCall("rangeTo", listOf(expr, other), origin)
        } else return super.tryReadPostfix(expr)
    }

    private fun namedCall(name: String, expr: Expression, origin: Int): Expression {
        return namedCall(name, listOf(expr), origin)
    }

    private fun namedCall(name: String, expr: List<Expression>, origin: Int): Expression {
        val nameExpr = UnresolvedFieldExpression(name, emptyList(), currPackage, origin)
        return CallExpression(nameExpr, emptyList(), expr.map { NamedParameter(null, it) }, origin)
    }

    fun readMethod() {
        val origin = origin(i)
        consume("def")
        val name = consumeName(VSCodeType.METHOD, 0)
        val valueParameters = pushCall {
            readParameterDeclarations(currPackage.typeWithArgs, emptyList())
        }
        consume(":")
        pushPythonBlock(ScopeType.METHOD, name) { methodScope ->
            val bodyExpr = readMethodBody()
            methodScope.selfAsMethod = Method(
                null, false, name, emptyList(), valueParameters,
                methodScope, null, emptyList(), bodyExpr, 0, origin
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

    override fun readParameterDeclarations(selfType: Type?, extra: List<Parameter>): List<Parameter> {
        val parameters = ArrayList<Parameter>(extra)
        loop@ while (i < tokens.size) {

            val isVal = false
            val origin = origin(i)
            val type = pythonInstanceType
            val name = consumeName(VSCodeType.PARAMETER, 0)

            // println("Found $name: $type = $initialValue at ${resolveOrigin(i)}")

            val keywords = packFlags()
            val parameter = Parameter(
                parameters.size, !isVal, isVal, isVararg = false, name, type,
                null, currPackage, origin
            )
            parameter.getOrCreateField(selfType, keywords)
            parameters.add(parameter)

            readComma()
        }
        return parameters
    }

    fun readWith(): TryCatchBlock {
        val origin = origin(i)
        consume("with")
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
        if (!consumeIf("if")) consume("elif")
        val condition = readExpression()
        consume(":")
        val body = pushPythonBlock(ScopeType.METHOD_BODY, "if") {
            readMethodBody()
        }
        val elseBranch = when {
            consumeIf("else") -> {
                consume(":")
                pushPythonBlock(ScopeType.METHOD_BODY, "if") {
                    readMethodBody()
                }
            }
            tokens.equals(i, "elif") -> {
                readIfBranch()
            }
            else -> null
        }
        return IfElseBranch(condition, body, elseBranch)
    }

}