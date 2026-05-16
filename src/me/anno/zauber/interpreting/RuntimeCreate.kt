package me.anno.zauber.interpreting

import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType

object RuntimeCreate {

    fun Runtime.createNumber(base: NumberExpression): Instance {
        val type = base.resolvedType ?: base.resolvedType0
        val rawValue = when (type) {
            Types.Byte -> base.asInt.toByte()
            Types.UByte -> base.asInt.toUByte()
            Types.Short -> base.asInt.toShort()
            Types.UShort -> base.asInt.toUShort()
            Types.Int -> base.asInt.toInt()
            Types.UInt -> base.asInt.toInt().toUInt()
            Types.Long -> base.asInt
            Types.ULong -> base.asInt.toULong()
            Types.Float -> base.asFloat.toFloat()
            Types.Double -> base.asFloat
            else -> throw NotImplementedError("Create instance of type $type")
        }
        val instance = getClass(type).createInstance()
        instance.rawValue = rawValue
        return instance
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

    fun Runtime.createChar(value: Char): Instance {
        val instance = getClass(Types.Char).createInstance()
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
        if (type.fields.isNotEmpty()) {
            val arrayType = ClassType(Types.Array.clazz, listOf(Types.Byte), -1)
            val content = getClass(arrayType).createInstance()
            val bytes = value.encodeToByteArray()
            content.fields[0] = createInt(bytes.size)
            content.rawValue = bytes
            instance.fields[0] = content
        } else {
            instance.rawValue = value
        }
        return instance
    }

}