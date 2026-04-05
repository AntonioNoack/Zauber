package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.interpreting.RuntimeCast.castToBool
import me.anno.zauber.interpreting.RuntimeCast.castToByte
import me.anno.zauber.interpreting.RuntimeCast.castToDouble
import me.anno.zauber.interpreting.RuntimeCast.castToFloat
import me.anno.zauber.interpreting.RuntimeCast.castToInt
import me.anno.zauber.interpreting.RuntimeCast.castToLong
import me.anno.zauber.interpreting.RuntimeCast.castToShort
import me.anno.zauber.interpreting.RuntimeCast.castToString
import me.anno.zauber.interpreting.RuntimeCreate.createByte
import me.anno.zauber.interpreting.RuntimeCreate.createDouble
import me.anno.zauber.interpreting.RuntimeCreate.createFloat
import me.anno.zauber.interpreting.RuntimeCreate.createInt
import me.anno.zauber.interpreting.RuntimeCreate.createLong
import me.anno.zauber.interpreting.RuntimeCreate.createShort
import me.anno.zauber.interpreting.RuntimeCreate.createString
import me.anno.zauber.logging.LogManager
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
        runtime.register(langScope, "println", listOf(Types.StringType)) { _, params ->
            runPrintln(castToString(params[0]))
        }
        runtime.register(langScope, "println", listOf(Types.IntType)) { _, params ->
            runPrintln(castToInt(params[0]).toString())
        }
    }

    private fun runPrintln(content: String): Instance {
        val rt = runtime
        rt.printed += content
        println(content)
        return rt.getUnit()
    }

    fun registerArrayAccess() {
        runtime.register(Types.ArrayType.clazz, "get", listOf(Types.IntType)) { self, params ->
            val index = castToInt(params[0])
            val rt = runtime
            when (val content = self.rawValue) {
                is Array<*> -> content[index] as Instance
                is BooleanArray -> rt.getBool(content[index])
                is ByteArray -> rt.createByte(content[index])
                is ShortArray -> rt.createShort(content[index])
                is IntArray -> rt.createInt(content[index])
                is LongArray -> rt.createLong(content[index])
                is FloatArray -> rt.createFloat(content[index])
                is DoubleArray -> rt.createDouble(content[index])
                null -> throw IllegalStateException("Missing array content")
                else -> throw IllegalStateException("Unknown array content: ${content.javaClass.simpleName}")
            }
        }
        runtime.register(
            Types.ArrayType.clazz,
            "set",
            listOf(Types.IntType, GenericType(Types.ArrayType.clazz, "V"))
        ) { self, params ->
            val index = castToInt(params[0])
            val value = params[1]
            val rt = runtime
            @Suppress("UNCHECKED_CAST")
            when (val content = self.rawValue) {
                is Array<*> -> (content as Array<Instance>)[index] = value
                is BooleanArray -> content[index] = castToBool(value)
                is ByteArray -> content[index] = castToByte(value)
                is ShortArray -> content[index] = castToShort(value)
                is IntArray -> content[index] = castToInt(value)
                is LongArray -> content[index] = castToLong(value)
                is FloatArray -> content[index] = castToFloat(value)
                is DoubleArray -> content[index] = castToDouble(value)
                null -> throw IllegalStateException("Missing array content")
                else -> throw IllegalStateException("Unknown array content: ${content.javaClass.simpleName}")
            }
            rt.getUnit()
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
        rt.registerBinaryMethod(Types.FloatType, "compareTo") { a, b ->
            val a = castToFloat(a)
            val b = castToFloat(b)
            rt.createInt(a.compareTo(b))
        }
    }

    fun registerStringMethods() {
        val rt = runtime
        rt.registerBinaryMethod(Types.StringType, "plus") { a, b ->
            val a = castToString(a)
            val b = castToString(b)
            rt.createString(a + b)
        }
    }

    fun Runtime.registerBinaryIntMethod(name: String, calc: (Int, Int) -> Int) {
        registerBinaryMethod(Types.IntType, name) { a, b ->
            LOGGER.info("Executing Int.$name ($a, $b)")
            val result = calc(castToInt(a), castToInt(b))
            createInt(result)
        }
    }

    fun Runtime.registerBinaryFloatMethod(name: String, calc: (Float, Float) -> Float) {
        registerBinaryMethod(Types.FloatType, name) { a, b ->
            LOGGER.info("Executing Float.$name ($a, $b)")
            val result = calc(castToFloat(a), castToFloat(b))
            createFloat(result)
        }
    }

}