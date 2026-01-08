package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.Instance
import me.anno.zauber.interpreting.Runtime

/**
 * Merges the result of an if and an else into one value.
 * This is equivalent to LLVM's phi instruction.
 * */
class SimpleMerge(
    dst: SimpleField,
    val ifBlock: SimpleBlock,
    val ifField: SimpleField,
    val elseBlock: SimpleBlock,
    val elseField: SimpleField,
    val base: Expression
) : SimpleAssignmentExpression(dst, base.scope, base.origin) {
    override fun toString(): String {
        return "$dst = Merge($ifField, $elseField)"
    }

    override fun eval(runtime: Runtime): Instance {
        TODO("How can we find out which branch we're coming from???")
    }
}