package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.expression.resolved.ThisExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.Instance
import me.anno.zauber.interpreting.Runtime

class SimpleThis(dst: SimpleField, val base: ThisExpression) :
    SimpleAssignmentExpression(dst, base.scope, base.origin) {

    override fun toString(): String {
        return "$dst = $base"
    }

    override fun eval(runtime: Runtime): Instance {
        throw NotImplementedError()
    }
}