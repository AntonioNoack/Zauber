package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.ast.simple.fields.SimpleInstruction
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope

class SimpleBoxCast(
    dst: SimpleField,
    val src: SimpleField,
    scope: Scope, origin: Long
) : SimpleAssignment(dst, scope, origin) {

    override fun toString(): String {
        return "$dst = (from ${src.type}) $src"
    }

    override fun execute(): BlockReturn? {
        val runtime = runtime
        runtime[dst] = runtime[src]
        return null
    }

    override fun clone(src: SimpleGraph, dst: SimpleGraph): SimpleInstruction {
        return SimpleBoxCast(
            src.cloned(this.dst, dst),
            src.cloned(this@SimpleBoxCast.src, dst),
            scope, origin
        )
    }

}