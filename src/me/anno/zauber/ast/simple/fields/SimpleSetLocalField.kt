package me.anno.zauber.ast.simple.fields

import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope

class SimpleSetLocalField(
    val field: LocalField,
    val value: SimpleField, scope: Scope, origin: Long
) : SimpleInstruction(scope, origin) {

    override fun execute(): BlockReturn? {
        val runtime = runtime
        runtime.getCall().localFields[field.id] = runtime[value]
        return null
    }

    override fun toString(): String {
        return "local#${field.id} = $value"
    }

}