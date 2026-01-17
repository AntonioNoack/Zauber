package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.simple.SimpleExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.interpreting.RuntimeThrown
import me.anno.zauber.types.Scope

class SimpleThrow(val field: SimpleField, scope: Scope, origin: Int) : SimpleExpression(scope, origin) {
    override fun toString(): String {
        return "throw $field"
    }

    override fun execute(runtime: Runtime) {
        val instance = runtime[field]
        throw RuntimeThrown(instance)
    }
}