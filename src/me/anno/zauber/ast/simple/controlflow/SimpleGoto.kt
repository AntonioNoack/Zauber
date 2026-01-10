package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope

class SimpleGoto(
    val condition: SimpleField?,
    val bodyBlock: SimpleBlock,
    val target: SimpleBlock,
    val isBreak: Boolean,
    scope: Scope, origin: Int
) : SimpleExpression(scope, origin) {

    override fun toString(): String {
        return "if(${condition ?: "true"}) goto ${target.hashCode()}"
    }

    override fun execute(runtime: Runtime) {
        if (condition == null) {
            runtime.gotoOtherBlock(target)
        } else {
            val condition = runtime[condition]
            if (runtime.castToBool(condition)) {
                runtime.gotoOtherBlock(target)
            }
        }
    }
}