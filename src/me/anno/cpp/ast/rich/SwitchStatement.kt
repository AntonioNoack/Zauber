package me.anno.cpp.ast.rich

import me.anno.zauber.ast.rich.controlflow.SubjectCondition
import me.anno.zauber.ast.rich.controlflow.SubjectConditionType
import me.anno.zauber.ast.rich.controlflow.SubjectWhenCase
import me.anno.zauber.ast.rich.controlflow.whenSubjectToIfElseChain
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.types.ScopeType

fun CppASTBuilder.readSwitch(label: String?): Expression {
    // `switch` already consumed
    val subject = readExpressionCondition()
    val scopeName = currPackage.generateName("switch")
    val cases = ArrayList<SubjectWhenCase>()
    val childScope = pushBlock(ScopeType.WHEN_CASES, scopeName) { scope ->
        var defaultCase: Expression? = null

        // todo if a block does not end with a 'break', we need to enter the next block
        while (!tokens.equals(i, TokenType.CLOSE_BLOCK)) {
            when {
                tokens.equals(i, "case") -> {
                    val values = ArrayList<Expression>()

                    // one or more `case X:`
                    do {
                        consume("case")
                        values += readExpression()
                        consume(":")
                    } while (tokens.equals(i, "case"))

                    val body = readCaseBody()
                    val conditions = values.map {
                        SubjectCondition(it, null, SubjectConditionType.EQUALS, null)
                    }
                    cases += SubjectWhenCase(conditions, scope, body)
                }
                consumeIf("default") -> {
                    if (defaultCase != null)
                        throw IllegalStateException("Duplicate default case at ${tokens.err(i)}")
                    consume(":")
                    defaultCase = readCaseBody()
                    cases += SubjectWhenCase(null, scope, defaultCase)
                }
                else -> throw IllegalStateException("Expected case/default in switch at ${tokens.err(i)}")
            }
        }
        scope
    }
    return whenSubjectToIfElseChain(childScope, subject, cases)
}

private fun CppASTBuilder.readCaseBody(): Expression {
    val origin = origin(i)
    val expressions = ArrayList<Expression>()

    while (i < tokens.size) {
        when {
            tokens.equals(i, "case") ||
                    tokens.equals(i, "default") ||
                    tokens.equals(i, TokenType.CLOSE_BLOCK) -> break
            tokens.equals(i, ";") -> i++ // skip
            else -> expressions += readExpression()
        }
    }

    return ExpressionList(expressions, currPackage, origin)
}
