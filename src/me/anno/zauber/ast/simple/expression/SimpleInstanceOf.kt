package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class SimpleInstanceOf(
    dst: SimpleField,
    val value: SimpleField,
    val type: Type,
    scope: Scope, origin: Int
) : SimpleAssignmentExpression(dst, scope, origin) {
    override fun toString(): String {
        return "$dst = $value is $type"
    }

    override fun eval(runtime: Runtime): BlockReturn {
        val instance = runtime[value]
        val givenType = instance.type
        val expectedType = runtime.getClass(type)
        val value = runtime.getBool(givenType.isSubTypeOf(expectedType))
        return BlockReturn(ReturnType.VALUE, value)
    }
}