package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.scope.Scope

class SimpleGetTypeFromInstance(
    dst: SimpleField,
    val value: SimpleField,
    scope: Scope, origin: Int
) : SimpleAssignment(dst, scope, origin) {

    override fun toString(): String = "$dst = $value::class"

    override fun eval(): BlockReturn {
        val runtime = Runtime.runtime
        val instance = runtime[value]
        val value = runtime.getTypeInstance(instance.clazz.type)
        return BlockReturn(ReturnType.VALUE, value)
    }
}