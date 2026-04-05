package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.interpreting.RuntimeCreate.createString

class SimpleString(dst: SimpleField, val base: StringExpression) :
    SimpleAssignment(dst, base.scope, base.origin) {

    override fun toString(): String {
        return "$dst = $base"
    }

    override fun eval(): BlockReturn {
        val value = runtime.createString(base.value)
        return BlockReturn(ReturnType.VALUE, value)
    }
}