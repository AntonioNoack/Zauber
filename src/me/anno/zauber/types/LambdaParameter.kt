package me.anno.zauber.types

class LambdaParameter(val name: String?, val type: Type) {
    override fun toString(): String {
        return "$name: $type"
    }
}