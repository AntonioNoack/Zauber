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
            Types.Byte -> createByte(value.toByte(basis))
            Types.Short -> createShort(value.toShort(basis))
            Types.Int -> createInt(value.toInt(basis))
            Types.Long -> createLong(value.toLong(basis))
            Types.Float -> createFloat(if (basis == 10) value.toFloat() else parseHexFloat(value).toFloat())
            Types.Double -> createDouble(if (basis == 10) value.toDouble() else parseHexFloat(value))
            else -> throw NotImplementedError("Create instance of type $type")
        }
    }

    fun Runtime.createByte(value: Byte): Instance {
        val instance = getClass(Types.Byte).createInstance()
        instance.rawValue = value
        return instance
    }

    fun Runtime.createShort(value: Short): Instance {
        val instance = getClass(Types.Short).createInstance()
        instance.rawValue = value
        return instance
    }

    fun Runtime.createInt(value: Int): Instance {
        val instance = getClass(Types.Int).createInstance()
        instance.rawValue = value
        return instance
    }

    fun Runtime.createLong(value: Long): Instance {
        val instance = getClass(Types.Long).createInstance()
        instance.rawValue = value
        return instance
    }

    fun Runtime.createFloat(value: Float): Instance {
        val instance = getClass(Types.Float).createInstance()
        instance.rawValue = value
        return instance
    }

    fun Runtime.createDouble(value: Double): Instance {
        val instance = getClass(Types.Double).createInstance()
        instance.rawValue = value
        return instance
    }

    fun Runtime.createString(value: String): Instance {
        val type = getClass(Types.String)
        val instance = type.createInstance()
        if (type.properties.isNotEmpty()) {
            val arrayType = ClassType(Types.Array.clazz, listOf(Types.Byte), -1)
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