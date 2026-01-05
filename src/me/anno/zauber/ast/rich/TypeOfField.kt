package me.anno.zauber.ast.rich

import me.anno.zauber.types.Type

class TypeOfField(val field: Field) : Type() {
    override fun toStringImpl(depth: Int): String {
        return "typeOf(${field.name})"
    }
}