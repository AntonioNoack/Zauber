package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.interpreting.RuntimeCreate.createString
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType

class Instance(
    val clazz: ZClass,
    val properties: Array<Instance?>,
    val id: Int
) {

    var rawValue: Any? = null

    override fun toString(): String {
        val rawValue = rawValue
        val prefix = "Instance@$id($clazz,${properties.toList()}"
        return when (rawValue) {
            null -> "$prefix)"
            is Array<*> -> "$prefix,${rawValue.toList()})"
            is IntArray -> "$prefix,I${rawValue.toList()})"
            is LongArray -> "$prefix,J${rawValue.toList()})"
            is FloatArray -> "$prefix,F${rawValue.toList()})"
            is DoubleArray -> "$prefix,D${rawValue.toList()})"
            is ByteArray -> "$prefix,B${rawValue.toList()})"
            is ShortArray -> "$prefix,S${rawValue.toList()})"
            is CharArray -> "$prefix,C${rawValue.joinToString()})"
            is BooleanArray -> "$prefix,Z${rawValue.toList()})"
            else -> "$prefix,$rawValue)"
        }
    }

    fun checkType(type1: Type) {
        check(clazz.type == type1) {
            "Casting $this to $type1 failed, type mismatch, ${clazz.type}"
        }
    }

    fun castToBool(): Boolean {
        val rt = runtime
        val isTrue = this == rt.getBool(true)
        val isFalse = this == rt.getBool(false)
        check(isTrue || isFalse) { "Expected value to be either true or false, got $this" }
        return isTrue
    }

    fun castToByte(): Byte {
        checkType(Types.Byte)
        return rawValue as? Byte
            ?: throw IllegalStateException("Found illegal Byte-instance without raw value: $this")
    }

    fun castToShort(): Short {
        checkType(Types.Short)
        return rawValue as? Short
            ?: throw IllegalStateException("Found illegal Short-instance without raw value: $this")
    }

    fun castToInt(): Int {
        checkType(Types.Int)
        return rawValue as? Int
            ?: throw IllegalStateException("Found illegal Int-instance without raw value: $this")
    }

    fun castToLong(): Long {
        checkType(Types.Long)
        return rawValue as? Long
            ?: throw IllegalStateException("Found illegal Long-instance without raw value: $this")
    }

    fun castToFloat(): Float {
        checkType(Types.Float)
        return rawValue as? Float
            ?: throw IllegalStateException("Found illegal Float-instance without raw value: $this")
    }

    fun castToDouble(): Double {
        checkType(Types.Double)
        return rawValue as? Double
            ?: throw IllegalStateException("Found illegal Double-instance without raw value: $this")
    }

    fun castToString(): String {
        checkType(Types.String)
        if (rawValue == null) {
            // a byte array
            val content = properties[0]!!
            val string = when (val bytes = content.rawValue) {
                is ByteArray -> bytes
                is Array<*> -> ByteArray(bytes.size) { (bytes[it] as Instance).castToByte() }
                else -> throw NotImplementedError()
            }.decodeToString()
            rawValue = string
            return string
        }
        return rawValue as String
    }

    fun castToType(): Type {
        val ct = clazz.type
        check(
            ct == Types.ClassType || ct == Types.TypeT ||
                    ct == Types.UnionType || ct == Types.GenericType
        )
        return rawValue as Type
    }

    fun cloneIfValue(): Instance {
        return if (clazz.isValueClass) clone() else this
    }

    fun clone(): Instance {
        check(clazz.type is ClassType)
        val newId = runtime.nextInstanceId()
        val newProperties = Array(properties.size) {
            properties[it]?.cloneIfValue()
        }
        return Instance(clazz, newProperties, newId)
    }

    fun set(fieldName: String, value: String) {
        val fieldIndex = clazz.properties.indexOfFirst { it.name == fieldName }
        if (fieldIndex < 0) return
        properties[fieldIndex] = runtime.createString(value)
    }
}