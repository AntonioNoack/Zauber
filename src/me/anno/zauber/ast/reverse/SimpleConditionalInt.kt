package me.anno.zauber.ast.reverse

import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.expression.SimpleAssignment
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.ast.simple.fields.SimpleInstruction
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.interpreting.RuntimeCreate.createInt
import me.anno.zauber.scope.Scope

class SimpleConditionalInt(
    dst: SimpleField,
    val condition: SimpleField,
    val ifTrue: Int,
    val ifFalse: Int,
    scope: Scope, origin: Long
) : SimpleAssignment(dst, scope, origin) {

    override fun execute(): BlockReturn? {
        val runtime = runtime
        runtime[dst] = runtime.createInt(if (runtime[condition].castToBool()) ifTrue else ifFalse)
        return null
    }

    override fun toString(): String {
        return "$dst = $condition ? $ifTrue : $ifFalse"
    }

    override fun hasInput(field: SimpleField): Boolean {
        return condition == field
    }

    override fun clone(src: SimpleGraph, dst: SimpleGraph): SimpleInstruction {
        return SimpleConditionalInt(
            src.cloned(this.dst, dst),
            src.cloned(condition, dst),
            ifTrue, ifFalse, scope, origin
        )
    }

}