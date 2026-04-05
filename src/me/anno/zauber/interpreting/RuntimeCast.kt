package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types

object RuntimeCast {

    fun checkType(value: Instance, type: Type) {
        check(value.type.type == type) {
            "Casting $value to $type failed, type mismatch, ${value.type.type}"
        }
    }

    fun castToBool(instance: Instance): Boolean {
        val rt = runtime
        val isTrue = instance == rt.getBool(true)
        val isFalse = instance == rt.getBool(false)
        check(isTrue || isFalse) { "Expected value to be either true or false, got $instance" }
        return isTrue
    }

    fun castToByte(value: Instance): Byte {
        checkType(value, Types.ByteType)
        return value.rawValue as Byte
    }

    fun castToShort(value: Instance): Short {
        checkType(value, Types.ShortType)
        return value.rawValue as Short
    }

    fun castToInt(value: Instance): Int {
        checkType(value, Types.IntType)
        return value.rawValue as? Int
            ?: throw IllegalStateException("Found illegal Int-instance without raw value: $value")
    }

    fun castToLong(value: Instance): Long {
        checkType(value, Types.LongType)
        return value.rawValue as Long
    }

    fun castToFloat(value: Instance): Float {
        checkType(value, Types.FloatType)
        return value.rawValue as Float
    }

    fun castToDouble(value: Instance): Double {
        checkType(value, Types.DoubleType)
        return value.rawValue as Double
    }

    fun castToString(value: Instance): String {
        checkType(value, Types.StringType)
        if (value.rawValue == null) {
            // a byte array
            val content = value.properties[0]!!
            val string = when (val bytes = content.rawValue) {
                is ByteArray -> bytes
                is Array<*> -> ByteArray(bytes.size) { castToByte(bytes[it] as Instance) }
                else -> throw NotImplementedError()
            }.decodeToString()
            value.rawValue = string
            return string
        }
        return value.rawValue as String
    }
}