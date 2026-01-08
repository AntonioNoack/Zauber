package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope

class SimpleBranch(
    val condition: SimpleField, val ifTrue: SimpleBlock, val ifFalse: SimpleBlock,
    scope: Scope, origin: Int
) : SimpleExpression(scope, origin) {

    override fun toString(): String {
        return "if ($condition) { $ifTrue } else { $ifFalse; }"
    }

    override fun execute(runtime: Runtime) {
        val condition = runtime[condition]
        val block = if (runtime.castToBool(condition)) ifTrue else ifFalse
        runtime.executeBlock(block)
    }

}