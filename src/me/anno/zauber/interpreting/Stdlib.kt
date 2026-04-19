package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.interpreting.RuntimeCreate.createByte
import me.anno.zauber.interpreting.RuntimeCreate.createChar
import me.anno.zauber.interpreting.RuntimeCreate.createDouble
import me.anno.zauber.interpreting.RuntimeCreate.createFloat
import me.anno.zauber.interpreting.RuntimeCreate.createInt
import me.anno.zauber.interpreting.RuntimeCreate.createLong
import me.anno.zauber.interpreting.RuntimeCreate.createShort
import me.anno.zauber.interpreting.RuntimeCreate.createString
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.Inheritance
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType

object Stdlib {

    private val LOGGER = LogManager.getLogger(Stdlib::class)

    fun Runtime.registerBinaryMethod(type: ClassType, name: String, calc: (Instance, Instance) -> Instance) {
        register(type.clazz, name, listOf(type)) { self, params ->
            calc(self, params[0])
        }
    }

    fun registerPrintln() {
        runtime.register(langScope, "println", listOf(Types.String)) { _, params ->
            runPrintln(params[0].castToString())
        }
        runtime.register(langScope, "println", listOf(Types.Int)) { _, params ->
            runPrintln(params[0].castToInt().toString())
        }
    }

    private fun runPrintln(content: String): Instance {
        val rt = runtime
        rt.printed += content
        println(content)
        return rt.getUnit()
    }

    fun registerArrayAccess() {
        runtime.register(
            Types.Array.clazz, "get",
            listOf(Types.Int)
        ) { self, (index0) ->
            check((self.clazz.type as ClassType).clazz == Types.Array.clazz) {
                "ClassCastException: $self is not an array"
            }
            val index = index0.castToInt()
            val rt = runtime
            when (val content = self.rawValue) {
                is Array<*> -> content[index] as Instance
                is BooleanArray -> rt.getBool(content[index])
                is ByteArray -> rt.createByte(content[index])
                is ShortArray -> rt.createShort(content[index])
                is CharArray -> rt.createChar(content[index])
                is IntArray -> rt.createInt(content[index])
                is LongArray -> rt.createLong(content[index])
                is FloatArray -> rt.createFloat(content[index])
                is DoubleArray -> rt.createDouble(content[index])
                null -> throw IllegalStateException("Missing array content in $self")
                else -> throw IllegalStateException("Unknown array content: ${content.javaClass.simpleName}")
            }
        }
        runtime.register(
            Types.Array.clazz, "set",
            listOf(Types.Int, GenericType(Types.Array.clazz, "V"))
        ) { self, (index0, value) ->
            check((self.clazz.type as ClassType).clazz == Types.Array.clazz) {
                "ClassCastException: $self is not an array"
            }
            val index = index0.castToInt()
            val rt = runtime
            @Suppress("UNCHECKED_CAST")
            when (val content = self.rawValue) {
                is Array<*> -> (content as Array<Instance>)[index] = value
                is BooleanArray -> content[index] = value.castToBool()
                is ByteArray -> content[index] = value.castToByte()
                is ShortArray -> content[index] = value.castToShort()
                is CharArray -> content[index] = value.castToChar()
                is IntArray -> content[index] = value.castToInt()
                is LongArray -> content[index] = value.castToLong()
                is FloatArray -> content[index] = value.castToFloat()
                is DoubleArray -> content[index] = value.castToDouble()
                null -> throw IllegalStateException("Missing array content")
                else -> throw IllegalStateException("Unknown array content: ${content.javaClass.simpleName}")
            }
            rt.getUnit()
        }
    }

    fun registerSmallIntMethods() {
        val rt = runtime
        rt.registerUnaryMethod(Types.Byte, "toChar") { self ->
            rt.createChar(self.castToByte().toInt().and(0xff).toChar())
        }
    }

    fun registerIntMethods() {
        val rt = runtime
        rt.registerBinaryIntMethod("plus", Int::plus)
        rt.registerBinaryIntMethod("minus", Int::minus)
        rt.registerBinaryIntMethod("times", Int::times)
        rt.registerBinaryIntMethod("div", Int::div)
        rt.registerBinaryIntMethod("compareTo", Int::compareTo)
    }

    fun registerFloatMethods() {
        val rt = runtime
        rt.registerBinaryFloatMethod("plus", Float::plus)
        rt.registerBinaryFloatMethod("minus", Float::minus)
        rt.registerBinaryFloatMethod("times", Float::times)
        rt.registerBinaryFloatMethod("div", Float::div)
        rt.registerBinaryMethod(Types.Float, "compareTo") { a, b ->
            rt.createInt(a.castToFloat().compareTo(b.castToFloat()))
        }
    }

    fun registerStringMethods() {
        val rt = runtime
        rt.registerBinaryMethod(Types.String, "plus") { a, b ->
            rt.createString(a.castToString() + b.castToString())
        }
        rt.registerBinaryMethod(Types.String, "split") { content, separator ->
            val contentI = content.castToString()
            val separator = separator.castToString()
            val parts0 = contentI.split(separator)
            val parts1 = Array(parts0.size) { rt.createString(parts0[it]) }
            content.clazz.createArray(parts1)
        }
        rt.register(Types.Any.clazz, "toString", emptyList()) { instance, _ ->
            val str = when (instance.clazz.type) {
                Types.Byte -> instance.castToByte().toString()
                Types.Short -> instance.castToShort().toString()
                Types.Char -> instance.castToChar().toString()
                Types.Int -> instance.castToInt().toString()
                Types.Long -> instance.castToLong().toString()
                Types.Float -> instance.castToFloat().toString()
                Types.Double -> instance.castToDouble().toString()
                Types.Boolean -> instance.castToBool().toString()
                Types.String -> instance.castToString()
                Types.Unit -> "Unit"
                else -> "${(instance.clazz.type)}@${instance.id}"
            }
            rt.createString(str)
        }
    }

    fun registerTypeMethods() {
        val rt = runtime
        rt.register(Types.TypeT.clazz, "isSubTypeOf", listOf(Types.TypeT)) { type, (otherType) ->
            rt.getBool(Inheritance.isSubTypeOf(expectedType = otherType.castToType(), actualType = type.castToType()))
        }
    }

    fun Runtime.registerBinaryIntMethod(name: String, calc: (a: Int, b: Int) -> Int) {
        registerBinaryMethod(Types.Int, name) { a, b ->
            LOGGER.info("Executing Int.$name ($a, $b)")
            val result = calc(a.castToInt(), b.castToInt())
            createInt(result)
        }
    }

    fun Runtime.registerBinaryFloatMethod(name: String, calc: (a: Float, b: Float) -> Float) {
        registerBinaryMethod(Types.Float, name) { a, b ->
            LOGGER.info("Executing Float.$name ($a, $b)")
            val result = calc(a.castToFloat(), b.castToFloat())
            createFloat(result)
        }
    }

    fun Runtime.registerUnaryMethod(selfType: ClassType, name: String, calc: (self: Instance) -> Instance) {
        register(selfType.clazz, name, emptyList()) { self, _ ->
            LOGGER.info("Executing ${selfType.clazz.pathStr}.$name ($self)")
            calc(self)
        }
    }

}