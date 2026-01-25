package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.Instance
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope

class SimpleGetField(
    dst: SimpleField,
    val self: SimpleField,
    val field: Field,
    scope: Scope, origin: Int
) : SimpleAssignmentExpression(dst, scope, origin) {

    override fun toString(): String {
        return "$dst = $self?[${field.selfType}].${field.name}"
    }

    override fun eval(runtime: Runtime): Instance {
        val self = runtime[self]
        println("Self for $field: $self")
        return runtime[self, field]
    }

}