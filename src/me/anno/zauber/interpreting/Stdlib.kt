package me.anno.zauber.interpreting

import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.Types.ArrayType
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
        runtime.register(ArrayType.clazz, "set", listOf(IntType, GenericType(ArrayType.clazz, "V"))) { rt, self, params ->
            val index = rt.castToInt(params[0])
            val value = params[1]
            @Suppress("UNCHECKED_CAST")
            val content = self.rawValue as Array<Instance>
            content[index] = value
            rt.getUnit()
        }
    }

    fun registerIntMethods(rt: Runtime) {
        rt.registerBinaryMethod(IntType, "plus") { a, b ->
            val a = rt.castToInt(a)
            val b = rt.castToInt(b)
            rt.createInt(a + b)
        }
        rt.registerBinaryMethod(IntType, "times") { a, b ->
            val a = rt.castToInt(a)
            val b = rt.castToInt(b)
            rt.createInt(a * b)
        }
    }
}