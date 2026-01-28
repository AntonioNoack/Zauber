package me.anno.zauber.interpreting

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
import me.anno.zauber.types.Types.ArrayType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType

object Stdlib {

    private val LOGGER = LogManager.getLogger(Stdlib::class)

    fun Runtime.registerBinaryMethod(type: ClassType, name: String, calc: (Instance, Instance) -> Instance) {
        register(type.clazz, name, listOf(type)) { _, self, params ->
            calc(self, params[0])
        }
    }

    fun registerPrintln(runtime: Runtime) {
        runtime.register(langScope, "println", listOf(StringType)) { rt, _, params ->
            val content = rt.castToString(params[0])
            rt.printed += content
            println(content)
            rt.getUnit()
        }
    }

    fun registerArrayAccess(runtime: Runtime) {
        runtime.register(ArrayType.clazz, "get", listOf(IntType)) { rt, self, params ->
            val index = rt.castToInt(params[0])
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
            }.apply {
                println("Array.get($index) returned $this")
            }
        }
        runtime.register(
            ArrayType.clazz,
            "set",
            listOf(IntType, GenericType(ArrayType.clazz, "V"))
        ) { rt, self, params ->
            val index = rt.castToInt(params[0])
            val value = params[1]
            @Suppress("UNCHECKED_CAST")
            when (val content = self.rawValue) {
                is Array<*> -> (content as Array<Instance>)[index] = value
                is BooleanArray -> content[index] = rt.castToBool(value)
                is ByteArray -> content[index] = rt.castToByte(value)
                is ShortArray -> content[index] = rt.castToShort(value)
                is IntArray -> content[index] = rt.castToInt(value)
                is LongArray -> content[index] = rt.castToLong(value)
                is FloatArray -> content[index] = rt.castToFloat(value)
                is DoubleArray -> content[index] = rt.castToDouble(value)
                null -> throw IllegalStateException("Missing array content")
                else -> throw IllegalStateException("Unknown array content: ${content.javaClass.simpleName}")
            }
            rt.getUnit()
        }
    }

    fun registerIntMethods(rt: Runtime) {
        rt.registerBinaryIntMethod("plus", Int::plus)
        rt.registerBinaryIntMethod("minus", Int::minus)
        rt.registerBinaryIntMethod("times", Int::times)
        rt.registerBinaryIntMethod("div", Int::div)
        rt.registerBinaryIntMethod("compareTo", Int::compareTo)
    }

    fun registerFloatMethods(rt: Runtime) {
        rt.registerBinaryFloatMethod("plus", Float::plus)
        rt.registerBinaryFloatMethod("minus", Float::minus)
        rt.registerBinaryFloatMethod("times", Float::times)
        rt.registerBinaryFloatMethod("div", Float::div)
        rt.registerBinaryMethod(FloatType, "compareTo") { a, b ->
            val a = rt.castToFloat(a)
            val b = rt.castToFloat(b)
            rt.createInt(a.compareTo(b))
        }
    }

    fun registerStringMethods(rt: Runtime) {
        rt.registerBinaryMethod(StringType, "plus") { a, b ->
            val a = rt.castToString(a)
            val b = rt.castToString(b)
            rt.createString(a + b)
        }
    }

    fun Runtime.registerBinaryIntMethod(name: String, calc: (Int, Int) -> Int) {
        registerBinaryMethod(IntType, name) { a, b ->
            LOGGER.info("Executing Int.$name ($a, $b)")
            val result = calc(castToInt(a), castToInt(b))
            createInt(result)
        }
    }

    fun Runtime.registerBinaryFloatMethod(name: String, calc: (Float, Float) -> Float) {
        registerBinaryMethod(FloatType, name) { a, b ->
            LOGGER.info("Executing Float.$name ($a, $b)")
            val result = calc(castToFloat(a), castToFloat(b))
            createFloat(result)
        }
    }

}