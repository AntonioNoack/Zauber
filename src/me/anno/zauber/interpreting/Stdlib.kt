package me.anno.zauber.interpreting

import me.anno.utils.Half
import me.anno.utils.Half.Companion.toHalf
import me.anno.utils.Maths.clamp
import me.anno.zauber.ast.rich.expression.constants.NumberExpression.Companion.isFloat
import me.anno.zauber.ast.simple.ASTSimplifier
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.interpreting.RuntimeCreate.createByte
import me.anno.zauber.interpreting.RuntimeCreate.createChar
import me.anno.zauber.interpreting.RuntimeCreate.createDouble
import me.anno.zauber.interpreting.RuntimeCreate.createFloat
import me.anno.zauber.interpreting.RuntimeCreate.createHalf
import me.anno.zauber.interpreting.RuntimeCreate.createInt
import me.anno.zauber.interpreting.RuntimeCreate.createLong
import me.anno.zauber.interpreting.RuntimeCreate.createShort
import me.anno.zauber.interpreting.RuntimeCreate.createString
import me.anno.zauber.interpreting.RuntimeCreate.createUByte
import me.anno.zauber.interpreting.RuntimeCreate.createUInt
import me.anno.zauber.interpreting.RuntimeCreate.createULong
import me.anno.zauber.interpreting.RuntimeCreate.createUShort
import me.anno.zauber.typeresolution.Inheritance
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType

/**
 * Standard library implementation for interpreter / compile-time execution
 * */
object Stdlib {

    fun Runtime.registerBinaryMethod(type: ClassType, name: String, calc: (Instance, Instance) -> Instance) {
        register(type.clazz, name, listOf(type)) { self, params ->
            calc(self, params[0])
        }
    }

    fun registerPrintln() {
        runtime.register(langScope, "println", listOf(Types.String)) { _, params ->
            runPrintln(params[0].castToString())
        }
        for (type in ASTSimplifier.nativeNumbers) {
            runtime.register(langScope, "println", listOf(type)) { _, params ->
                val value = params[0]
                check(value.clazz.type == type)
                check(value.rawValue != null)
                runPrintln(value.rawValue.toString())
            }
        }
    }

    private fun runPrintln(content: String): Instance {
        val rt = runtime
        rt.printed.append(content).append('\n')
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
                null -> error("Missing array content in $self")
                else -> error("Unknown array content: ${content.javaClass.simpleName}")
            }
        }
        runtime.register(
            Types.Array.clazz, "set",
            listOf(Types.Int, GenericType(Types.Array.clazz, "V"))
        ) { self, (index, value) ->
            arraySet(self, index, value)
            runtime.getUnit()
        }
        // todo why is this needed for "testListOfLambdasTotallyExplicit"?
        runtime.register(
            Types.Array.clazz, "set",
            listOf(Types.Int, Types.NullableAny)
        ) { self, (index, value) ->
            arraySet(self, index, value)
            runtime.getUnit()
        }
    }

    private fun arraySet(self: Instance, index: Instance, value: Instance) {
        check((self.clazz.type as ClassType).clazz == Types.Array.clazz) {
            "ClassCastException: $self is not an array"
        }
        val index1 = index.castToInt()
        @Suppress("UNCHECKED_CAST")
        when (val content = self.rawValue) {
            is Array<*> -> (content as Array<Instance>)[index1] = value
            is BooleanArray -> content[index1] = value.castToBool()
            is ByteArray -> content[index1] = value.castToByte()
            is ShortArray -> content[index1] = value.castToShort()
            is CharArray -> content[index1] = value.castToChar()
            is IntArray -> content[index1] = value.castToInt()
            is LongArray -> content[index1] = value.castToLong()
            is FloatArray -> content[index1] = value.castToFloat()
            is DoubleArray -> content[index1] = value.castToDouble()
            null -> error("Missing array content")
            else -> error("Unknown array content: ${content.javaClass.simpleName}")
        }
    }

    fun registerSmallIntMethods() {
        val rt = runtime
        rt.registerUnaryMethod(Types.Byte, "toChar") { self ->
            rt.createChar(self.castToByte().toInt().and(0xff).toChar())
        }
    }

    fun registerByteMethods() {
        val rt = runtime
        rt.registerBinaryByteMethod("plus", Byte::plus)
        rt.registerBinaryByteMethod("minus", Byte::minus)
        rt.registerBinaryByteMethod("times", Byte::times)
        rt.registerBinaryByteMethod("div", Byte::div)
        rt.registerBinaryByteMethod("rem", Byte::rem)
        rt.registerBinaryByteMethod("compareTo", Byte::compareTo)
    }

    fun registerUByteMethods() {
        val rt = runtime
        rt.registerBinaryUByteMethod("plus", UByte::plus)
        rt.registerBinaryUByteMethod("minus", UByte::minus)
        rt.registerBinaryUByteMethod("times", UByte::times)
        rt.registerBinaryUByteMethod("div", UByte::div)
        rt.registerBinaryUByteMethod("rem", UByte::rem)
        rt.registerBinaryMethod(Types.UByte, "compareTo") { a, b ->
            rt.createInt(a.castToUByte().compareTo(b.castToUByte()))
        }
    }

    fun registerShortMethods() {
        val rt = runtime
        rt.registerBinaryShortMethod("plus", Short::plus)
        rt.registerBinaryShortMethod("minus", Short::minus)
        rt.registerBinaryShortMethod("times", Short::times)
        rt.registerBinaryShortMethod("div", Short::div)
        rt.registerBinaryShortMethod("rem", Short::rem)
        rt.registerBinaryShortMethod("compareTo", Short::compareTo)
    }

    fun registerUShortMethods() {
        val rt = runtime
        rt.registerBinaryUShortMethod("plus", UShort::plus)
        rt.registerBinaryUShortMethod("minus", UShort::minus)
        rt.registerBinaryUShortMethod("times", UShort::times)
        rt.registerBinaryUShortMethod("div", UShort::div)
        rt.registerBinaryUShortMethod("rem", UShort::rem)
        rt.registerBinaryMethod(Types.UShort, "compareTo") { a, b ->
            rt.createInt(a.castToUShort().compareTo(b.castToUShort()))
        }
    }

    fun registerIntMethods() {
        val rt = runtime
        rt.registerBinaryIntMethod("plus", Int::plus)
        rt.registerBinaryIntMethod("minus", Int::minus)
        rt.registerBinaryIntMethod("times", Int::times)
        rt.registerBinaryIntMethod("div", Int::div)
        rt.registerBinaryIntMethod("rem", Int::rem)
        rt.registerBinaryIntMethod("shl", Int::shl)
        rt.registerBinaryIntMethod("shr", Int::shr)
        rt.registerBinaryIntMethod("ushr", Int::ushr)
        rt.registerBinaryIntMethod("compareTo", Int::compareTo)
    }

    fun registerUIntMethods() {
        val rt = runtime
        rt.registerBinaryUIntMethod("plus", UInt::plus)
        rt.registerBinaryUIntMethod("minus", UInt::minus)
        rt.registerBinaryUIntMethod("times", UInt::times)
        rt.registerBinaryUIntMethod("div", UInt::div)
        rt.registerBinaryUIntMethod("rem", UInt::rem)
        rt.registerBinaryMethod(Types.UInt, "compareTo") { a, b ->
            rt.createInt(a.castToUInt().compareTo(b.castToUInt()))
        }
    }

    fun registerLongMethods() {
        val rt = runtime
        rt.registerBinaryLongMethod("plus", Long::plus)
        rt.registerBinaryLongMethod("minus", Long::minus)
        rt.registerBinaryLongMethod("times", Long::times)
        rt.registerBinaryLongMethod("div", Long::div)
        rt.registerBinaryLongMethod("rem", Long::rem)
        rt.registerBinaryMethod(Types.Long, "compareTo") { a, b ->
            rt.createInt(a.castToLong().compareTo(b.castToLong()))
        }
    }

    fun registerULongMethods() {
        val rt = runtime
        rt.registerBinaryULongMethod("plus", ULong::plus)
        rt.registerBinaryULongMethod("minus", ULong::minus)
        rt.registerBinaryULongMethod("times", ULong::times)
        rt.registerBinaryULongMethod("div", ULong::div)
        rt.registerBinaryULongMethod("rem", ULong::rem)
        rt.registerBinaryMethod(Types.ULong, "compareTo") { a, b ->
            rt.createInt(a.castToULong().compareTo(b.castToULong()))
        }
    }

    fun registerHalfMethods() {
        val rt = runtime
        rt.registerBinaryHalfMethod("plus", Half::plus)
        rt.registerBinaryHalfMethod("minus", Half::minus)
        rt.registerBinaryHalfMethod("times", Half::times)
        rt.registerBinaryHalfMethod("div", Half::div)
        rt.registerBinaryHalfMethod("rem", Half::rem)
        rt.registerBinaryMethod(Types.Half, "compareTo") { a, b ->
            rt.createInt(a.castToHalf().compareTo(b.castToHalf()))
        }
    }

    fun registerFloatMethods() {
        val rt = runtime
        rt.registerBinaryFloatMethod("plus", Float::plus)
        rt.registerBinaryFloatMethod("minus", Float::minus)
        rt.registerBinaryFloatMethod("times", Float::times)
        rt.registerBinaryFloatMethod("div", Float::div)
        rt.registerBinaryFloatMethod("rem", Float::rem)
        rt.registerBinaryMethod(Types.Float, "compareTo") { a, b ->
            rt.createInt(a.castToFloat().compareTo(b.castToFloat()))
        }
    }

    fun registerDoubleMethods() {
        val rt = runtime
        rt.registerBinaryDoubleMethod("plus", Double::plus)
        rt.registerBinaryDoubleMethod("minus", Double::minus)
        rt.registerBinaryDoubleMethod("times", Double::times)
        rt.registerBinaryDoubleMethod("div", Double::div)
        rt.registerBinaryDoubleMethod("rem", Double::rem)
        rt.registerBinaryMethod(Types.Double, "compareTo") { a, b ->
            rt.createInt(a.castToDouble().compareTo(b.castToDouble()))
        }
    }

    fun registerNumberConversions() {
        val types = ASTSimplifier.nativeNumbers
        val rt = runtime
        for (i in types.indices) {
            for (j in types.indices) {
                val fromType = types[i]
                val toType = types[j]

                rt.registerUnaryMethod(fromType, "to${toType.clazz.name}") { from ->
                    if (fromType.isFloat()) {
                        val fromValue = getFloatValue(from, fromType)
                        rt.createNumberFromFloat(fromValue, toType)
                    } else {
                        val fromValue = getIntValue(from, fromType)
                        rt.createNumberFromInt(fromValue, toType)
                    }
                }
            }
        }
    }

    private fun getFloatValue(from: Instance, fromType: Type): Double {
        return when (fromType) {
            Types.Half -> from.castToHalf().toDouble()
            Types.Float -> from.castToFloat().toDouble()
            Types.Double -> from.castToDouble()
            else -> throw NotImplementedError()
        }
    }

    private fun getIntValue(from: Instance, fromType: Type): Long {
        return when (fromType) {
            Types.Char -> from.castToChar().code.toLong()
            Types.Byte -> from.castToByte().toLong()
            Types.UByte -> from.castToUByte().toLong()
            Types.Short -> from.castToShort().toLong()
            Types.UShort -> from.castToUShort().toLong()
            Types.Int -> from.castToInt().toLong()
            Types.UInt -> from.castToUInt().toLong()
            Types.Long -> from.castToLong()
            Types.ULong -> from.castToULong().toLong()
            else -> throw NotImplementedError()
        }
    }

    private fun Runtime.createNumberFromFloat(from: Double, toType: Type): Instance {
        return when (toType) {
            Types.Half -> createHalf(from.toHalf())
            Types.Float -> createFloat(from.toFloat())
            Types.Double -> createDouble(from)

            Types.Char -> createChar(from.toInt().toChar())
            Types.Byte -> createByte(clamp(from.toInt(), -128, 127).toByte())
            Types.UByte -> createUByte(clamp(from.toInt(), 0, 255).toUByte())
            Types.Short -> createShort(clamp(from.toInt(), -0x8000, 0x7fff).toShort())
            Types.UShort -> createUShort(clamp(from.toInt(), 0, 0xffff).toUShort())
            Types.Int -> createInt(from.toInt())
            Types.UInt -> createUInt(from.toUInt())
            Types.Long -> createLong(from.toLong())
            Types.ULong -> createULong(from.toULong())
            else -> throw NotImplementedError("Create $toType from Double")
        }
    }

    private fun Runtime.createNumberFromInt(from: Long, toType: Type): Instance {
        return when (toType) {
            Types.Half -> createHalf(from.toFloat().toHalf())
            Types.Float -> createFloat(from.toFloat())
            Types.Double -> createDouble(from.toDouble())

            Types.Char -> createChar(from.toInt().toChar())
            Types.Byte -> createByte(from.toByte())
            Types.UByte -> createUByte(from.toUByte())
            Types.Short -> createShort(from.toShort())
            Types.UShort -> createUShort(from.toUShort())
            Types.Int -> createInt(from.toInt())
            Types.UInt -> createUInt(from.toUInt())
            Types.Long -> createLong(from)
            Types.ULong -> createULong(from.toULong())
            else -> throw NotImplementedError("Create $toType from Long")
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
                Types.UByte -> instance.castToUByte().toString()
                Types.Short -> instance.castToShort().toString()
                Types.UShort -> instance.castToUShort().toString()
                Types.Char -> instance.castToChar().toString()
                Types.Int -> instance.castToInt().toString()
                Types.UInt -> instance.castToUInt().toString()
                Types.Long -> instance.castToLong().toString()
                Types.ULong -> instance.castToULong().toString()
                Types.Half -> instance.castToHalf().toString()
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

    fun registerAllMethods() {
        registerSmallIntMethods()

        registerByteMethods()
        registerUByteMethods()

        registerShortMethods()
        registerUShortMethods()

        registerIntMethods()
        registerUIntMethods()

        registerLongMethods()
        registerULongMethods()

        registerHalfMethods()
        registerFloatMethods()
        registerDoubleMethods()

        registerNumberConversions()

        registerStringMethods()
        registerPrintln()
        registerArrayAccess()
        registerTypeMethods()
    }

    fun Runtime.registerBinaryByteMethod(name: String, calc: (a: Byte, b: Byte) -> Int) {
        registerBinaryMethod(Types.Byte, name) { a, b ->
            val result = calc(a.castToByte(), b.castToByte())
            createInt(result)
        }
    }

    fun Runtime.registerBinaryUByteMethod(name: String, calc: (a: UByte, b: UByte) -> UInt) {
        registerBinaryMethod(Types.UByte, name) { a, b ->
            val result = calc(a.castToUByte(), b.castToUByte())
            createUInt(result)
        }
    }

    fun Runtime.registerBinaryShortMethod(name: String, calc: (a: Short, b: Short) -> Int) {
        registerBinaryMethod(Types.Short, name) { a, b ->
            val result = calc(a.castToShort(), b.castToShort())
            createInt(result)
        }
    }

    fun Runtime.registerBinaryUShortMethod(name: String, calc: (a: UShort, b: UShort) -> UInt) {
        registerBinaryMethod(Types.UShort, name) { a, b ->
            val result = calc(a.castToUShort(), b.castToUShort())
            createUInt(result)
        }
    }

    fun Runtime.registerBinaryIntMethod(name: String, calc: (a: Int, b: Int) -> Int) {
        registerBinaryMethod(Types.Int, name) { a, b ->
            val result = calc(a.castToInt(), b.castToInt())
            createInt(result)
        }
    }

    fun Runtime.registerBinaryUIntMethod(name: String, calc: (a: UInt, b: UInt) -> UInt) {
        registerBinaryMethod(Types.UInt, name) { a, b ->
            val result = calc(a.castToUInt(), b.castToUInt())
            createUInt(result)
        }
    }

    fun Runtime.registerBinaryLongMethod(name: String, calc: (a: Long, b: Long) -> Long) {
        registerBinaryMethod(Types.Long, name) { a, b ->
            val result = calc(a.castToLong(), b.castToLong())
            createLong(result)
        }
    }

    fun Runtime.registerBinaryULongMethod(name: String, calc: (a: ULong, b: ULong) -> ULong) {
        registerBinaryMethod(Types.ULong, name) { a, b ->
            val result = calc(a.castToULong(), b.castToULong())
            createULong(result)
        }
    }

    fun Runtime.registerBinaryHalfMethod(name: String, calc: (a: Half, b: Half) -> Half) {
        registerBinaryMethod(Types.Half, name) { a, b ->
            val result = calc(a.castToHalf(), b.castToHalf())
            createHalf(result)
        }
    }

    fun Runtime.registerBinaryFloatMethod(name: String, calc: (a: Float, b: Float) -> Float) {
        registerBinaryMethod(Types.Float, name) { a, b ->
            val result = calc(a.castToFloat(), b.castToFloat())
            createFloat(result)
        }
    }

    fun Runtime.registerBinaryDoubleMethod(name: String, calc: (a: Double, b: Double) -> Double) {
        registerBinaryMethod(Types.Double, name) { a, b ->
            val result = calc(a.castToDouble(), b.castToDouble())
            createDouble(result)
        }
    }

    fun Runtime.registerUnaryMethod(selfType: ClassType, name: String, calc: (self: Instance) -> Instance) {
        register(selfType.clazz, name, emptyList()) { self, _ -> calc(self) }
    }

}