package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.simple.SimpleExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.types.Scope

class SimpleSetField(
    val self: SimpleField?,
    val field: Field,
    val src: SimpleField,
    scope: Scope, origin: Int
) : SimpleExpression(scope, origin) {
    override fun toString(): String {
        return "$self?[${field.selfType}].${field.name} = $src"
    }
}