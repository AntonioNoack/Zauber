package me.anno.zauber.interpreting

import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.DoubleType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.LongType
import me.anno.zauber.types.Types.StringType

object RuntimeCast {

    fun checkType(value: Instance, type: Type) {
        check(value.type.type == type) {
            "Casting $value to $type failed, type mismatch, ${value.type.type}"
        }
    }

    fun Runtime.castToBool(instance: Instance): Boolean {
        val isTrue = instance == getBool(true)
        val isFalse = instance == getBool(false)
        check(isTrue || isFalse) { "Expected value to be either true or false, got $instance" }
        return isTrue
    }

    fun Runtime.castToInt(value: Instance): Int {
        checkType(value, IntType)
        return value.rawValue as Int
    }

    fun Runtime.castToLong(value: Instance): Long {
        checkType(value, LongType)
        return value.rawValue as Long
    }

    fun Runtime.castToFloat(value: Instance): Float {
        checkType(value, FloatType)
        return value.rawValue as Float
    }

    fun Runtime.castToDouble(value: Instance): Double {
        checkType(value, DoubleType)
        return value.rawValue as Double
    }

    fun Runtime.castToString(value: Instance): String {
        checkType(value, StringType)
        return value.rawValue as String
    }
}