package me.anno.zauber.astbuilder.expression

import me.anno.zauber.types.Type

open class LambdaVariable(val type: Type?, val name: String) {
    override fun toString(): String {
        return if (type != null) "$name: $type" else name
    }
}