package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
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

    override fun eval(runtime: Runtime): BlockReturn {
        val newInstance = runtime.getClass(method.selfType).createInstance()
        println("Created instance of ${method.selfType}, invoking constructor")

        val rfm = runtime.executeCall(newInstance, method, valueParameters)
        if (rfm.type != ReturnType.RETURN) return rfm

        println("Invoked constructor, checking")
        check(newInstance.properties.all { it != null }) {
            "Some field in $newInstance was not initialized by constructor"
        }
        println("Returning new instance: $newInstance")
        return BlockReturn(ReturnType.VALUE, newInstance)
    }

}