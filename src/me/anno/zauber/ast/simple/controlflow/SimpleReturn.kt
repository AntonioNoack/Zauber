package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.simple.SimpleExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.ReturnFromMethod
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope

class SimpleReturn(val field: SimpleField, scope: Scope, origin: Int) : SimpleExpression(scope, origin) {
    override fun toString(): String {
        return "return $field"
    }

    override fun execute(runtime: Runtime): ReturnFromMethod {
        val value = runtime[field]
        return ReturnFromMethod(ReturnType.RETURN, value)
    }
}