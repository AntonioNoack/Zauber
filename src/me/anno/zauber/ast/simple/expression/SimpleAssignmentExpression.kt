package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.SimpleExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.Instance
import me.anno.zauber.interpreting.ReturnFromMethod
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope

abstract class SimpleAssignmentExpression(val dst: SimpleField, scope: Scope, origin: Int) :
    SimpleExpression(scope, origin) {

    override fun execute(runtime: Runtime): ReturnFromMethod? {
        val value = eval(runtime)
        runtime[dst] = value
        return null
    }

    abstract fun eval(runtime: Runtime): Instance

}