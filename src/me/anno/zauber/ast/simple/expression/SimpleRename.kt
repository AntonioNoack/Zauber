package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.ast.simple.fields.SimpleInstruction
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope

class SimpleRename(
    dst: SimpleField,
    val src: SimpleField,
    scope: Scope, origin: Long
) : SimpleAssignment(dst, scope, origin) {

    override fun toString(): String {
        return "$dst = $src"
    }

    override fun execute(): BlockReturn? {
        val runtime = runtime
        runtime[dst] = runtime[src]
        return null
    }

    override fun hasInput(field: SimpleField): Boolean = src == field

    override fun clone(src: SimpleGraph, dst: SimpleGraph): SimpleInstruction {
        return SimpleRename(
            src.cloned(this.dst, dst),
            src.cloned(this@SimpleRename.src, dst),
            scope, origin
        )
    }

}