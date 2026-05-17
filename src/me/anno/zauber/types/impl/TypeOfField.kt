package me.anno.zauber.types.impl

import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.types.Type

class TypeOfField(val field: Field) : Type() {
    override fun toStringImpl(depth: Int): String {
        return "typeOf(${field.name})"
    }
}