package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.types.Type

open class LambdaVariable(val type: Type?, val field: Field) {

    val name get() = field.name

    override fun toString(): String {
        return if (type != null) "$name: $type" else name
    }
}