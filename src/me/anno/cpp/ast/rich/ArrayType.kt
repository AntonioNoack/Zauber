package me.anno.cpp.ast.rich

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.types.Type

class ArrayType(val baseType: Type, val size: Expression) : Type() {
    override fun toStringImpl(depth: Int): String {
        TODO("Not yet implemented")
    }
}