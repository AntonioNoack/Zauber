package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.simple.SimpleExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope

class SimpleSelfConstructor(
    val isThis: Boolean,
    val method: Constructor,
    val valueParameters: List<SimpleField>,
    scope: Scope, origin: Int
) : SimpleExpression(scope, origin) {

    init {
        check(method.valueParameters.size == valueParameters.size)
    }

    override fun toString(): String {
        (0 until 1).reversed()
        return "${if (isThis) "this" else "super"}${valueParameters.joinToString(", ", "](", ")")}"
    }

    override fun execute(runtime: Runtime) {
        runtime.executeCall(runtime.getThis(), method, valueParameters)
    }
}