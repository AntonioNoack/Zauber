package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.simple.SimpleExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.types.Scope

class SimpleReturn(val field: SimpleField, scope: Scope, origin: Int) : SimpleExpression(scope, origin) {
    override fun toString(): String {
        return "return $field"
    }
}