package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.simple.SimpleExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.types.Scope

class SimpleGetField(
    val dst: SimpleField,
    val self: SimpleField?,
    val field: Field,
    scope: Scope, origin: Int
) : SimpleExpression(scope, origin) {
    override fun toString(): String {
        return "$dst = $self?[${field.selfType}].${field.name}"
    }
}