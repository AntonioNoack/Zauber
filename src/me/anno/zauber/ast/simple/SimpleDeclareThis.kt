package me.anno.zauber.ast.simple

import me.anno.zauber.interpreting.ReturnFromMethod
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.interpreting.This
import me.anno.zauber.types.Scope

class SimpleDeclareThis(val thisScope: Scope, val field: SimpleField, scope: Scope, origin: Int) :
    SimpleExpression(scope, origin) {

    override fun toString(): String {
        return "this@$thisScope := $field;"
    }

    override fun execute(runtime: Runtime): ReturnFromMethod? {
        val instance = runtime[field]
        runtime.thisStack.add(This(instance, thisScope))
        return null
    }
}