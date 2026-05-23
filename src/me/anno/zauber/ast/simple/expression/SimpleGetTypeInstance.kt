package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Type

class SimpleGetTypeInstance(
    dst: SimpleField,
    val type: Type,
    scope: Scope, origin: Long
) : SimpleAssignment(dst, scope, origin) {

    override fun toString(): String = "$dst = $type::class"

    override fun execute(): BlockReturn? {
        val runtime = runtime
        runtime[dst] = runtime.getTypeInstance(type)
        return null
    }
}