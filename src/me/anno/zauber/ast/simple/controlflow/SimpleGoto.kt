package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.types.Scope

class SimpleGoto(val condition: SimpleField?, val target: SimpleBlock, scope: Scope, origin: Int) :
    SimpleExpression(scope, origin) {

    override fun toString(): String {
        return "if(${condition ?: "true"}) goto ${target.hashCode()}"
    }
}