package me.anno.zauber.types

data class LambdaParameter(val name: String?, val type: Type, val origin: Long) {
    override fun toString(): String {
        return "$name: $type"
    }

    override fun equals(other: Any?): Boolean {
        return other is LambdaParameter && name == other.name && type == other.type
    }

    override fun hashCode(): Int {
        return name.hashCode() * 31 + type.hashCode()
    }
}