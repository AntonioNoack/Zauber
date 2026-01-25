package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.simple.SimpleExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope

// todo this is very special...
//  idk if we even should have a SimpleYield, or if we should "simplify" it in-place
class SimpleYield(val field: SimpleField, scope: Scope, origin: Int) : SimpleExpression(scope, origin) {
    override fun toString(): String {
        return "yield $field"
    }

    override fun execute(runtime: Runtime): BlockReturn {
        val value = runtime[field]
        return BlockReturn(ReturnType.YIELD, value)
    }
}