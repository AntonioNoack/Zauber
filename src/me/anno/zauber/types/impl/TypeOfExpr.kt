package me.anno.zauber.types.impl

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.types.Type

class TypeOfExpr(val expr: Expression) : Type() {
    override fun toStringImpl(depth: Int): String {
        return "typeOf($expr)"
    }
}