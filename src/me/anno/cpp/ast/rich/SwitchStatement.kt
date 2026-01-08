package me.anno.cpp.ast.rich

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.NamedParameter
import me.anno.zauber.ast.rich.ZauberASTBuilder.Companion.syntheticList
import me.anno.zauber.ast.rich.controlflow.IfElseBranch
import me.anno.zauber.ast.rich.controlflow.createNamedBlock
import me.anno.zauber.ast.rich.controlflow.storeSubject
import me.anno.zauber.ast.rich.expression.*
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Types.BooleanType

fun CppASTBuilder.readSwitch(label: String?): Expression {

    val switchValue0 = readExpressionCondition()
    val switchValue = storeSubject(currPackage, switchValue0)

    // `switch` already consumed
    val bodyInstr = ArrayList<Expression>()
    val origin = origin(i)

    val trueExpr = SpecialValueExpression(SpecialValue.TRUE, currPackage, origin)
    val falseExpr = SpecialValueExpression(SpecialValue.FALSE, currPackage, origin)

    val scopeName = currPackage.generateName("switch")
    val bodyScope = pushBlock(ScopeType.WHEN_CASES, scopeName) { scope ->

        val noPrevBranch = Field(
            scope, null, true, null,
            "__hadPrevBranch", BooleanType, trueExpr, syntheticList, origin
        )
        val prevBranchContinues = Field(
            scope, null, true, null,
            "__prevBranchContinues", BooleanType, falseExpr, syntheticList, origin
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
                    do {
                        consume("case")
                        values += readExpression()
                        consume(":")
                    } while (tokens.equals(i, "case"))

                    val equalsCondition = values.map {
                        CheckEqualsOp(
                            it, switchValue, false, false,
                            scope, origin
                        ) as Expression
                    }.reduce { a, b -> a.or(b) }

                    val normalCase = noPrevBranchExpr.and(equalsCondition) // todo should probably use shortcutting...
                    val totalCondition = normalCase.or(prevBranchContinueExpr)

                    val scopeName = scope.generateName("case")
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
                    // condition: noPrevBranch | prevBranchContinue
                    consume(":")
                    val totalCondition = noPrevBranchExpr.or(prevBranchContinueExpr)
                    val scopeName = scope.generateName("default")
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

private fun Expression.and(other: Expression): Expression {
    return NamedCallExpression(
        this, "and", emptyList(),
        listOf(NamedParameter(null, other)), scope, origin
    ).apply { resolvedType = BooleanType }
}

private fun Expression.or(other: Expression): Expression {
    return NamedCallExpression(
        this, "or", emptyList(),
        listOf(NamedParameter(null, other)), scope, origin
    ).apply { resolvedType = BooleanType }
}

private fun CppASTBuilder.readCaseBody(): ArrayList<Expression> {
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
    return expressions
}
