package me.anno.zauber.astbuilder.controlflow

import me.anno.zauber.astbuilder.expression.Expression
import me.anno.zauber.astbuilder.expression.ExpressionList
import me.anno.zauber.types.Scope

class WhenCase(val condition: Expression?, val body: Expression) {
    override fun toString(): String {
        return "${condition ?: "else"} -> { $body }"
    }
}

fun whenBranchToIfElseChain(cases: List<WhenCase>, scope: Scope, origin: Int): Expression {
    var chain: Expression? = null
    for (i in cases.indices.reversed()) {
        val caseI = cases[i]
        chain = if (caseI.condition != null) {
            IfElseBranch(caseI.condition, caseI.body, chain)
        } else {
            if (chain != null) throw IllegalStateException("Else must be the last case")
            caseI.body
        }
    }
    return chain ?: ExpressionList(emptyList(), scope, origin)
}