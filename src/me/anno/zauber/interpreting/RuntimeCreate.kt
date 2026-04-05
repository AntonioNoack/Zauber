package me.anno.zauber.interpreting

import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.NumberExpression.Companion.parseHexFloat
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType

object RuntimeCreate {

    fun Runtime.createNumber(base: NumberExpression): Instance {
        val type = base.resolvedType ?: base.resolvedType0
        var value = base.value
        val basis = when {
            value.startsWith("0x", true) -> {
                value = value.substring(2)
                16
            }
            value.startsWith("0b", true) -> {
                value = value.substring(2)
                2
            }
            else -> 10
        }
        return when (type) {
            Types.ByteType -> createByte(value.toByte(basis))
            Types.ShortType -> createShort(value.toShort(basis))
            Types.IntType -> createInt(value.toInt(basis))
            Types.LongType -> createLong(value.toLong(basis))
            Types.FloatType -> createFloat(if (basis == 10) value.toFloat() else parseHexFloat(value).toFloat())
            Types.DoubleType -> createDouble(if (basis == 10) value.toDouble() else parseHexFloat(value))
            else -> throw NotImplementedError("Create instance of type $type")
        }
    }

    fun Runtime.createByte(value: Byte): Instance {
        val instance = getClass(Types.ByteType).createInstance()
        instance.rawValue = value
        return instance
    }

    fun Runtime.createShort(value: Short): Instance {
        val instance = getClass(Types.ShortType).createInstance()
        instance.rawValue = value
        return instance
    }

    fun Runtime.createInt(value: Int): Instance {
        val instance = getClass(Types.IntType).createInstance()
        instance.rawValue = value
        return instance
    }

    fun Runtime.createLong(value: Long): Instance {
        val instance = getClass(Types.LongType).createInstance()
        instance.rawValue = value
        return instance
    }

    fun Runtime.createFloat(value: Float): Instance {
        val instance = getClass(Types.FloatType).createInstance()
        instance.rawValue = value
        return instance
    }

    fun Runtime.createDouble(value: Double): Instance {
        val instance = getClass(Types.DoubleType).createInstance()
        instance.rawValue = value
        return instance
    }

    fun Runtime.createString(value: String): Instance {
        val type = getClass(Types.StringType)
        val instance = type.createInstance()
        if (type.properties.isNotEmpty()) {
            val arrayType = ClassType(Types.ArrayType.clazz, listOf(Types.ByteType), -1)
            val content = getClass(arrayType).createInstance()
            val bytes = value.encodeToByteArray()
            content.properties[0] = createInt(bytes.size)
            content.rawValue = bytes
            instance.properties[0] = content
        } else {
            instance.rawValue = value
        }
        return instance
    }

}