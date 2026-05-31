package me.anno.zauber.ast.simple.fields

import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.types.Specialization

interface SimpleGSetClassField {
    val self: SimpleField
    val field: Field
    val specialization: Specialization

    fun withField(field: Field): SimpleInstruction

    @Deprecated("Will always return false")
    fun isLocalField(): Boolean = false
}