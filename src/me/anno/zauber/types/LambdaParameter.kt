package me.anno.zauber.types

class LambdaParameter(val name: String?, val type: Type, val origin: Long) {
    override fun toString(): String {
        return "$name: $type"
    }
}