package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.Instance
import me.anno.zauber.interpreting.Runtime

class SimpleNumber(dst: SimpleField, val base: NumberExpression) :
    SimpleAssignmentExpression(dst, base.scope, base.origin) {

    override fun toString(): String {
        return "$dst = $base"
    }

    override fun eval(runtime: Runtime): Instance {
        return runtime.createNumber(base)
    }
}