package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope

class SimpleGetField(
    dst: SimpleField,
    val self: SimpleField,
    val field: Field,
    scope: Scope, origin: Int
) : SimpleAssignment(dst, scope, origin) {

    override fun toString(): String {
        return "$dst = $self?[${field.selfType}].${field.name}"
    }

    override fun eval(runtime: Runtime): BlockReturn {
        val self = runtime[self]
        // println("Self for $field: $self")
        val value = runtime[self, field]
        return BlockReturn(ReturnType.VALUE, value)
    }
}