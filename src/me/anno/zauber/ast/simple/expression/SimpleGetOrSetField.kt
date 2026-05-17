package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.ast.simple.SimpleInstruction
import me.anno.zauber.types.Specialization

interface SimpleGetOrSetField {
    val self: SimpleField
    val field: Field
    val specialization: Specialization

    fun withField(field: Field): SimpleInstruction
}