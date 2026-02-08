package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.simple.SimpleInstruction
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope

class SimpleSetField(
    val self: SimpleField,
    val field: Field,
    val value: SimpleField,
    scope: Scope, origin: Int
) : SimpleInstruction(scope, origin) {
    override fun toString(): String {
        return "$self?[${field.selfType}].${field.name} = $value"
    }

    override fun execute(runtime: Runtime): BlockReturn? {
        val selfInstance = runtime[self]
        val value = runtime[value]
        println("[SET] $selfInstance.${field.name} = $value")
        runtime[selfInstance, field] = value
        return null
    }
}