package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.simple.SimpleExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope

class SimpleYield(val field: SimpleField, scope: Scope, origin: Int) : SimpleExpression(scope, origin) {
    override fun toString(): String {
        return "yield $field"
    }

    override fun execute(runtime: Runtime) {
        val value = runtime[field]
        runtime.yieldFromCall(value)
    }
}