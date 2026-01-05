package me.anno.zauber.ast.simple

import me.anno.zauber.ast.rich.expression.Expression

object ASTSimplifier {
    fun simplify(expr: Expression): SimpleGraph {
        val graph = SimpleGraph()
        simplify(expr, graph.entry, graph)
        return graph
    }

    private fun simplify(expr: Expression, addToBlock: SimpleBlock, graph: SimpleGraph): SimpleBlock {
        when (expr) {
            else -> TODO("Simplify ${expr.javaClass.simpleName}")
        }
    }
}