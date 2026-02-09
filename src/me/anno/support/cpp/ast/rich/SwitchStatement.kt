package me.anno.support.cpp.ast.rich

import me.anno.support.java.ast.JavaASTBuilder
import me.anno.support.java.ast.NamedCastExpression
import me.anno.support.java.ast.NamedDestructuringExpression
import me.anno.zauber.ast.rich.Keywords
import me.anno.zauber.ast.rich.ZauberASTBuilderBase
import me.anno.zauber.ast.rich.controlflow.IfElseBranch
import me.anno.zauber.ast.rich.controlflow.createNamedBlock
import me.anno.zauber.ast.rich.controlflow.storeSubject
import me.anno.zauber.ast.rich.expression.CheckEqualsOp
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.IsInstanceOfExpr
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.expression.unresolved.AssignmentExpression
import me.anno.zauber.ast.rich.expression.unresolved.FieldExpression
import me.anno.zauber.ast.rich.expression.unresolved.LambdaVariable
import me.anno.zauber.ast.rich.expression.unresolved.NamedCallExpression
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Types.BooleanType

fun ZauberASTBuilderBase.readSwitch(label: String?): Expression {

    val origin = origin(i - 1) // in switch
    val switchValue0 = readExpressionCondition()
    val switchValue = storeSubject(currPackage, switchValue0)

    // `switch` already consumed
    val bodyInstr = ArrayList<Expression>()

    val trueExpr = SpecialValueExpression(SpecialValue.TRUE, currPackage, origin)
    val falseExpr = SpecialValueExpression(SpecialValue.FALSE, currPackage, origin)

    val scopeName = currPackage.generateName("switch", origin)
    var isSwitchWithExpression = false

    fun checkExpr(): String {
        if (!isSwitchWithExpression && tokens.equals(i, "->")) {
            isSwitchWithExpression = true
        }
        return if (isSwitchWithExpression) "->" else ":"
    }

    val bodyScope = pushBlock(ScopeType.WHEN_CASES, scopeName) { scope ->
        scope.breakLabel = label ?: ""

        val noPrevBranch = scope.addField(
            null, false, isMutable = true, null,
            "__hadPrevBranch", BooleanType, trueExpr, Keywords.SYNTHETIC, origin
        )
        val prevBranchContinues = scope.addField(
            null, false, isMutable = true, null,
            "__prevBranchContinues", BooleanType, falseExpr, Keywords.SYNTHETIC, origin
        )

        val noPrevBranchExpr = FieldExpression(noPrevBranch, scope, origin)
        val prevBranchContinueExpr = FieldExpression(prevBranchContinues, scope, origin)

        if (i < tokens.size && !tokens.equals(i, "case", "default")) {
            readCaseBody() // not executed, but read for fields
        }

        // if a block does not end with a 'break', we need to enter the next block
        while (i < tokens.size) {
            when {
                tokens.equals(i, "case") -> {
                    // condition: (noPrevBranch & equals) | prevBranchContinues
                    val values = ArrayList<Expression>()

                    val origin = origin(i)

                    // one or more `case X:`
                    consume("case")
                    while (true) {

                        var foundCondition = false
                        if (this is JavaASTBuilder && this !is CppASTBuilder) {
                            val k = i
                            val tn = readTypeAndName()
                            if (tn != null && tn.first != null && tokens.equals(i, "->")) {
                                val origin = origin(i)
                                val type = tn.first!!
                                val name = tn.second
                                values += NamedCastExpression(IsInstanceOfExpr(switchValue, type, scope, origin), name)
                                foundCondition = true
                            } else {
                                i = k
                                if (tokens.equals(i, TokenType.NAME) && // todo <- this type could be complex
                                    tokens.equals(i + 1, TokenType.OPEN_CALL) &&
                                    tokens.equals(
                                        tokens.findBlockEnd(
                                            i + 1,
                                            TokenType.OPEN_CALL,
                                            TokenType.CLOSE_CALL
                                        ) + 1,
                                        "->"
                                    )
                                ) {
                                    // lol, Rust-level destructuring
                                    // case QuicDatagram(var connection, var _, var _)
                                    //  -> we may have to read the type from the class...
                                    //  -> we need to read these properties already in the ASTScanner
                                    //  -> just use componentN()
                                    // todo we need a new scope for this...
                                    val scope = currPackage
                                    val type = scope.resolveType(tokens.toString(i++), this)
                                    val names = ArrayList<LambdaVariable?>()
                                    pushCall {
                                        while (i < tokens.size) {
                                            val origin = origin(i)
                                            val type = if (consumeIf("var")) null
                                            else readTypeNotNull(null, true)
                                            val name = tokens.toString(i++)
                                            if (name == "_") names.add(null)
                                            else {
                                                val getterName = "component${names.size + 1}"
                                                val initialValue =
                                                    NamedCallExpression(switchValue, getterName, scope, origin)
                                                val field = scope.addField(
                                                    null, false, isMutable = false, null,
                                                    name, type, initialValue,
                                                    Keywords.NONE, origin
                                                )
                                                names += LambdaVariable(type, field)
                                            }
                                            readComma()
                                        }
                                    }
                                    values += NamedDestructuringExpression(type, names, scope, origin)
                                    foundCondition = true
                                }
                            }
                        }

                        if (!foundCondition) {
                            values += push(findCaseEnd()) {
                                readExpression()
                            }
                            i-- // undo skipping '->'/':'
                        }

                        if (consumeIf("case")) continue // normal C/C++/Java
                        if (consumeIf(",")) continue // supported for Java switch-expr

                        break
                    }

                    // the end
                    consume(checkExpr())

                    val equalsCondition = values.map {
                        if (it is NamedCastExpression || it is NamedDestructuringExpression) it
                        else CheckEqualsOp(it, switchValue, byPointer = false, false, null, scope, origin)
                    }.reduce { a, b -> a.or(b) }

                    val normalCase = noPrevBranchExpr.and(equalsCondition) // todo should probably use shortcutting...
                    val totalCondition = normalCase.or(prevBranchContinueExpr)

                    val scopeName = scope.generateName("case", origin)
                    lateinit var bodyScope1: Scope
                    val body = pushScope(scopeName, ScopeType.METHOD_BODY) { bodyScopeI ->
                        bodyScope1 = bodyScopeI
                        readCaseBody()
                    }

                    // we found a branch
                    body.add(0, AssignmentExpression(noPrevBranchExpr, falseExpr))
                    // we assume there is a break flag
                    body.add(0, AssignmentExpression(prevBranchContinueExpr, falseExpr))
                    // if the end is reached, we mark the continue flag
                    body.add(AssignmentExpression(prevBranchContinueExpr, trueExpr))
                    bodyInstr.add(IfElseBranch(totalCondition, ExpressionList(body, bodyScope1, origin), null))
                }
                consumeIf("default") -> {
                    val origin = origin(i - 1) // on default
                    // condition: noPrevBranch | prevBranchContinue
                    consume(checkExpr())
                    val totalCondition = noPrevBranchExpr.or(prevBranchContinueExpr)
                    val scopeName = scope.generateName("default", origin)
                    lateinit var bodyScope1: Scope
                    val body = pushScope(scopeName, ScopeType.METHOD_BODY) { bodyScopeI ->
                        bodyScope1 = bodyScopeI
                        readCaseBody()
                    }
                    // flags are not interesting anymore after this
                    bodyInstr.add(IfElseBranch(totalCondition, ExpressionList(body, bodyScope1, origin), null))
                }
                else -> throw IllegalStateException("Expected case/default in switch at ${tokens.err(i)}")
            }
        }
        scope
    }

    val body = ExpressionList(bodyInstr, bodyScope, origin)
    return createNamedBlock(body, label, currPackage, origin)
}

fun ZauberASTBuilderBase.findCaseEnd(): Int {
    var depth = 0
    var i = i
    while (i < tokens.size) {
        when (tokens.getType(i)) {
            TokenType.SEMICOLON, TokenType.COMMA -> if (depth == 0) return i
            TokenType.OPEN_CALL, TokenType.OPEN_ARRAY, TokenType.OPEN_BLOCK -> depth++
            TokenType.CLOSE_CALL, TokenType.CLOSE_ARRAY, TokenType.CLOSE_BLOCK -> depth--
            else -> if (depth == 0 && tokens.equals(i, "->", ":", "case", "default")) return i
        }
        i++
    }
    return tokens.size
}

private fun Expression.and(other: Expression): Expression {
    return NamedCallExpression(this, "and", other, scope, origin)
        .apply { resolvedType = BooleanType }
}

private fun Expression.or(other: Expression): Expression {
    return NamedCallExpression(this, "or", other, scope, origin)
        .apply { resolvedType = BooleanType }
}

private fun ZauberASTBuilderBase.readCaseBody(): ArrayList<Expression> {
    return push(findCaseEnd()) {
        val list = readMethodBody().list
        list as? ArrayList<Expression> ?: ArrayList(list)
    }
}
