package me.anno.zauber.ast.simple.constants

import me.anno.utils.StringStyles
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.simple.expression.SimpleAssignment
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.interpreting.RuntimeCreate.createNumber

class SimpleNumber(dst: SimpleField, val base: NumberExpression) :
    SimpleAssignment(dst, base.scope, base.origin) {

    override fun toString(): String {
        return "$dst = ${StringStyles.style(base.value, StringStyles.DARK_BLUE)}"
    }

    override fun execute(): BlockReturn? {
        // fast-path, because it cannot crash
        val runtime = runtime
        runtime[dst] = runtime.createNumber(base, dst.type)
        return null
    }
}