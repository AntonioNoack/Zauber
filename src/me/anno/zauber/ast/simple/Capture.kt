package me.anno.zauber.ast.simple

import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.rich.member.MethodLike

data class Capture(val owner: MethodLike, val field: Field) {
    init {
        val owner = field.ownerScope
        if (owner.isObjectLike()) {
            throw IllegalArgumentException("Capturing object properties like $field is unnecessary, use SimpleGetObject instead")
        }
    }
}