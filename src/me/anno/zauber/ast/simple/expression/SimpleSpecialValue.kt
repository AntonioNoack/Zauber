package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.simple.SimpleField

class SimpleSpecialValue(dst: SimpleField, val base: SpecialValueExpression) :
    SimpleAssignmentExpression(dst, base.scope, base.origin) {

    override fun toString(): String {
        return "$dst = $base"
    }
}