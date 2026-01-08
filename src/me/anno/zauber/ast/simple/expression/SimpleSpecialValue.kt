package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.Instance
import me.anno.zauber.interpreting.Runtime

class SimpleSpecialValue(dst: SimpleField, val base: SpecialValueExpression) :
    SimpleAssignmentExpression(dst, base.scope, base.origin) {

    override fun toString(): String {
        return "$dst = $base"
    }

    override fun eval(runtime: Runtime): Instance {
        return when (base.type) {
            SpecialValue.SUPER, SpecialValue.THIS -> runtime.getThis()
            SpecialValue.NULL -> runtime.getNull()
            SpecialValue.TRUE -> runtime.getBool(true)
            SpecialValue.FALSE -> runtime.getBool(false)
        }
    }
}