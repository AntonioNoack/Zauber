package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.expression.CompareType
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.types.Scope

class SimpleCompare(dst: SimpleField, val base: SimpleField, val type: CompareType, scope: Scope, origin: Int) :
    SimpleAssignmentExpression(dst, scope, origin) {

    override fun toString(): String {
        return "$dst = $base ${type.symbol} 0"
    }
}