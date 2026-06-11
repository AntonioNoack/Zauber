package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.ast.simple.fields.SimpleInstruction
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.scope.Scope

class SimpleGetTypeFromInstance(
    dst: SimpleField,
    val value: SimpleField,
    scope: Scope, origin: Long
) : SimpleAssignment(dst, scope, origin) {

    override fun toString(): String = "$dst = $value::class"

    override fun execute(): BlockReturn? {
        val runtime = Runtime.runtime
        val instance = runtime[value]
        runtime[dst] = runtime.getTypeInstance(instance.clazz.type)
        return null
    }

    override fun clone(src: SimpleGraph, dst: SimpleGraph): SimpleInstruction {
        return SimpleGetTypeFromInstance(
            src.cloned(this.dst, dst),
            src.cloned(value, dst),
            scope, origin
        )
    }

}