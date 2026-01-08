package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.Instance
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class SimpleInstanceOf(
    dst: SimpleField,
    val src: SimpleField,
    val type: Type,
    scope: Scope, origin: Int
) : SimpleAssignmentExpression(dst, scope, origin) {
    override fun toString(): String {
        return "$dst = $src is $type"
    }

    override fun eval(runtime: Runtime): Instance {
        val instance = runtime[src]
        val givenType = instance.type
        val expectedType = runtime.getClass(type)
        return runtime.getBool(givenType.isSubTypeOf(expectedType))
    }
}