package me.anno.zauber.interpreting

import me.anno.utils.Half
import me.anno.utils.Half.Companion.toHalf
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType

object RuntimeCreate {

    fun Runtime.createNumber(base: NumberExpression, type: ClassType): Instance {
        val rawValue = when (type) {
            Types.Byte -> base.asInt.toByte()
            Types.UByte -> base.asInt.toUByte()
            Types.Short -> base.asInt.toShort()
            Types.UShort -> base.asInt.toUShort()
            Types.Int -> base.asInt.toInt()
            Types.UInt -> base.asInt.toInt().toUInt()
            Types.Long -> base.asInt
            Types.ULong -> base.asInt.toULong()
            Types.Half -> base.asFloat.toHalf()
            Types.Float -> base.asFloat.toFloat()
            Types.Double -> base.asFloat
            Types.Char -> base.asInt.toInt().toChar()
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

    fun Runtime.createUByte(value: UByte): Instance {
        val instance = getClass(Types.UByte).createInstance()
        instance.rawValue = value
        return instance
    }

    fun Runtime.createShort(value: Short): Instance {
        val instance = getClass(Types.Short).createInstance()
        instance.rawValue = value
        return instance
    }

    fun Runtime.createUShort(value: UShort): Instance {
        val instance = getClass(Types.UShort).createInstance()
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

    fun Runtime.createUInt(value: UInt): Instance {
        val instance = getClass(Types.UInt).createInstance()
        instance.rawValue = value
        return instance
    }

    fun Runtime.createLong(value: Long): Instance {
        val instance = getClass(Types.Long).createInstance()
        instance.rawValue = value
        return instance
    }

    fun Runtime.createULong(value: ULong): Instance {
        val instance = getClass(Types.ULong).createInstance()
        instance.rawValue = value
        return instance
    }

    fun Runtime.createHalf(value: Half): Instance {
        val instance = getClass(Types.Half).createInstance()
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
        if (instance.hasProperty("content")) {
            val bytes = value.encodeToByteArray()
            instance["content"] = createByteArray(bytes)
        }
        instance.rawValue = value
        return instance
    }


    fun Runtime.createByteArray(content: ByteArray): Instance {
        val clazz = getClass(Types.Array.withTypeParameter(Types.Byte))
        val instance = clazz.createInstance()
        instance.rawValue = content
        instance["size"] = createInt(content.size)
        return instance
    }

    fun Runtime.createPointer(type: ClassType, base: Instance, offset: Long = 0L): Instance {
        val clazz = getClass(Types.Pointer.withTypeParameter(type))
        val instance = clazz.createInstance()
        instance["base"] = base
        instance["offset"] = createLong(offset)
        return instance
    }

}