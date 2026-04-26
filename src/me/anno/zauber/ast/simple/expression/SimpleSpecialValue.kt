package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope

class SimpleSpecialValue(dst: SimpleField, val type: SpecialValue, scope: Scope, origin: Int) :
    SimpleAssignment(dst, scope, origin) {

    override fun toString(): String {
        return "$dst = $type"
    }

    override fun eval(): BlockReturn {
        val runtime = runtime
        val value = when (type) {
            SpecialValue.NULL -> runtime.getNull()
            SpecialValue.TRUE -> runtime.getBool(true)
            SpecialValue.FALSE -> runtime.getBool(false)
        }
        return BlockReturn(ReturnType.VALUE, value)
    }
}