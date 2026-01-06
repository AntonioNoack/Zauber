package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.SimpleExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.types.Scope

class SimpleCheckIdentical(
    val dst: SimpleField, val a: SimpleField, val b: SimpleField,
    val negated: Boolean,
    scope: Scope, origin: Int
) : SimpleExpression(scope, origin) {

    override fun toString(): String {
        return "$dst = $a ${if (negated) "!==" else "==="} $b"
    }
}