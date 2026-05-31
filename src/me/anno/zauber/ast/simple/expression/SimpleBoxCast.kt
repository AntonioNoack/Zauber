package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope

class SimpleBoxCast(
    dst: SimpleField,
    val value: SimpleField,
    scope: Scope, origin: Long
) : SimpleAssignment(dst, scope, origin) {

    override fun toString(): String {
        return "$dst = (boxing) $value"
    }

    override fun execute(): BlockReturn? {
        val runtime = runtime
        runtime[dst] = runtime[value]
        return null
    }
}