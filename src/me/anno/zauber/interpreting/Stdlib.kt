package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.RuntimeCast.castToFloat
import me.anno.zauber.interpreting.RuntimeCast.castToInt
import me.anno.zauber.interpreting.RuntimeCast.castToString
import me.anno.zauber.interpreting.RuntimeCreate.createFloat
import me.anno.zauber.interpreting.RuntimeCreate.createInt
import me.anno.zauber.interpreting.RuntimeCreate.createString
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.Types.ArrayType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType

object Stdlib {

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
            val content = self.rawValue as Array<*>
            content[index] as Instance
        }
        runtime.register(
            ArrayType.clazz,
            "set",
            listOf(IntType, GenericType(ArrayType.clazz, "V"))
        ) { rt, self, params ->
            val index = rt.castToInt(params[0])
            val value = params[1]

            @Suppress("UNCHECKED_CAST")
            val content = self.rawValue as Array<Instance>
            content[index] = value
            rt.getUnit()
        }
    }

    fun registerIntMethods(rt: Runtime) {
        rt.registerBinaryIntMethod(IntType, "plus", Int::plus)
        rt.registerBinaryIntMethod(IntType, "minus", Int::minus)
        rt.registerBinaryIntMethod(IntType, "times", Int::times)
        rt.registerBinaryIntMethod(IntType, "div", Int::div)
        rt.registerBinaryIntMethod(IntType, "compareTo", Int::compareTo)
    }

    fun registerFloatMethods(rt: Runtime) {
        rt.registerBinaryFloatMethod(FloatType, "plus", Float::plus)
        rt.registerBinaryFloatMethod(FloatType, "minus", Float::minus)
        rt.registerBinaryFloatMethod(FloatType, "times", Float::times)
        rt.registerBinaryFloatMethod(FloatType, "div", Float::div)
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

    fun Runtime.registerBinaryIntMethod(type: ClassType, name: String, calc: (Int, Int) -> Int) {
        registerBinaryMethod(type, name) { a, b ->
            val result = calc(castToInt(a), castToInt(b))
            createInt(result)
        }
    }

    fun Runtime.registerBinaryFloatMethod(type: ClassType, name: String, calc: (Float, Float) -> Float) {
        registerBinaryMethod(type, name) { a, b ->
            val result = calc(castToFloat(a), castToFloat(b))
            createFloat(result)
        }
    }

}