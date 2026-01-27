package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.interpreting.RuntimeCreate.createNumber

class SimpleNumber(dst: SimpleField, val base: NumberExpression) :
    SimpleAssignmentExpression(dst, base.scope, base.origin) {

    override fun toString(): String {
        return "$dst = $base"
    }

    override fun eval(runtime: Runtime): BlockReturn {
        return BlockReturn(ReturnType.VALUE, runtime.createNumber(base))
    }
}