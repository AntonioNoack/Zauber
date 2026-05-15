package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Type

class SimpleGetTypeInstance(
    dst: SimpleField,
    val type: Type,
    scope: Scope, origin: Long
) : SimpleAssignment(dst, scope, origin) {

    override fun toString(): String = "$dst = $type::class"

    override fun eval(): BlockReturn {
        val value = Runtime.runtime.getTypeInstance(type)
        return BlockReturn(ReturnType.VALUE, value)
    }
}