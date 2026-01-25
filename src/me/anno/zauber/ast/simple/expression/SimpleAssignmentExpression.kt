package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.SimpleExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope

abstract class SimpleAssignmentExpression(val dst: SimpleField, scope: Scope, origin: Int) :
    SimpleExpression(scope, origin) {

    override fun execute(runtime: Runtime): BlockReturn? {
        val value = eval(runtime)
        return if (value.type == ReturnType.VALUE) {
            runtime[dst] = value.instance
            null
        } else value
    }

    abstract fun eval(runtime: Runtime): BlockReturn

}