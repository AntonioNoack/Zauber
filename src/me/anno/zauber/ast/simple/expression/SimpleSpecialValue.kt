package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope

class SimpleSpecialValue(dst: SimpleField, val type: SpecialValue, scope: Scope, origin: Int) :
    SimpleAssignmentExpression(dst, scope, origin) {

    override fun toString(): String {
        return "$dst = $type"
    }

    override fun eval(runtime: Runtime): BlockReturn {
        val value = when (type) {
            SpecialValue.SUPER -> runtime.getThis()
            SpecialValue.NULL -> runtime.getNull()
            SpecialValue.TRUE -> runtime.getBool(true)
            SpecialValue.FALSE -> runtime.getBool(false)
        }
        return BlockReturn(ReturnType.VALUE, value)
    }
}