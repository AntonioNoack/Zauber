package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.ast.simple.SimpleNode
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime

/**
 * Merges the result of an if and an else into one value.
 * This is equivalent to LLVM's phi instruction.
 * */
class SimpleMerge(
    dst: SimpleField,
    val ifBlock: SimpleNode,
    val ifField: SimpleField,
    val elseBlock: SimpleNode,
    val elseField: SimpleField,
    val base: Expression
) : SimpleAssignment(dst, base.scope, base.origin) {

    init {
        // todo is this ok???? we might loose values/assignments
        // check(ifField.mergeInfo == null) { "Expected ifField to not already have mergeInfo" }
        // check(elseField.mergeInfo == null) { "Expected elseField to not already have mergeInfo" }
        ifField.mergeInfo = this
        elseField.mergeInfo = this
    }

    override fun toString(): String {
        return "$dst = Merge($ifField, $elseField)"
    }

    override fun eval(runtime: Runtime): BlockReturn {
        return BlockReturn(ReturnType.VALUE, runtime[dst])
    }
}