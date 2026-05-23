package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.fields.SimpleField
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
}