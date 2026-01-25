package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime

class SimpleSpecialValue(dst: SimpleField, val base: SpecialValueExpression) :
    SimpleAssignmentExpression(dst, base.scope, base.origin) {

    override fun toString(): String {
        return "$dst = $base"
    }

    override fun eval(runtime: Runtime): BlockReturn {
        val value = when (base.type) {
            SpecialValue.SUPER -> runtime.getThis()
            SpecialValue.NULL -> runtime.getNull()
            SpecialValue.TRUE -> runtime.getBool(true)
            SpecialValue.FALSE -> runtime.getBool(false)
        }
        return BlockReturn(ReturnType.VALUE, value)
    }
}