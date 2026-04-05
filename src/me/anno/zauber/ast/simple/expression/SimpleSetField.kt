package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.ast.simple.SimpleInstruction
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope

class SimpleSetField(
    val self: SimpleField,
    val field: Field,
    val value: SimpleField,
    scope: Scope, origin: Int
) : SimpleInstruction(scope, origin) {
    override fun toString(): String {
        return "$self?[${field.selfType}].${field.name} = $value"
    }

    override fun execute(): BlockReturn? {
        val runtime = runtime
        val selfInstance = runtime[self, this]
        val value = runtime[value, this]
        println("[SET] $selfInstance.${field.name} = $value")
        runtime[selfInstance, field] = value
        return null
    }
}