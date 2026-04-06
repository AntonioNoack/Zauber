package me.anno.zauber.interpreting

class Instance(
    val type: ZClass,
    val properties: Array<Instance?>,
    val id: Int
) {

    var rawValue: Any? = null

    override fun toString(): String {
        val rawValue = rawValue
        val prefix = "Instance@$id($type,${properties.toList()}"
        return when (rawValue) {
            null -> "$prefix)"
            is Array<*> -> "$prefix,${rawValue.toList()})"
            is IntArray -> "$prefix,I${rawValue.toList()})"
            is LongArray -> "$prefix,J${rawValue.toList()})"
            is FloatArray -> "$prefix,F${rawValue.toList()})"
            is DoubleArray -> "$prefix,D${rawValue.toList()})"
            is ByteArray -> "$prefix,B${rawValue.toList()})"
            is ShortArray -> "$prefix,S${rawValue.toList()})"
            is BooleanArray -> "$prefix,Z${rawValue.toList()})"
            else -> "$prefix,$rawValue)"
        }
    }
}