package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.Instance
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope

class SimpleConstructor(
    dst: SimpleField,
    val method: Constructor,
    val valueParameters: List<SimpleField>,
    scope: Scope, origin: Int
) : SimpleAssignmentExpression(dst, scope, origin) {

    init {
        check(method.valueParameters.size == valueParameters.size)
    }

    override fun toString(): String {
        return "$dst = new[${method.selfType}${valueParameters.joinToString(", ", "](", ")")}"
    }

    override fun eval(runtime: Runtime): Instance {
        val newInstance = runtime.getClass(method.selfType!!).createInstance()
        runtime.executeCall(newInstance, method, valueParameters)
        check(newInstance.properties.all { it != null }) {
            "Some field in $newInstance was not initialized by constructor"
        }
        return newInstance
    }

}