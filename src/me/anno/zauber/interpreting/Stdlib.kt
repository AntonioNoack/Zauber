package me.anno.zauber.interpreting

import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.impl.ClassType

object Stdlib {

    fun Runtime.registerBinaryMethod(type: ClassType, name: String, calc: (Instance, Instance) -> Instance) {
        register(type.clazz, name, listOf(type)) { rt, self, params ->
            calc(self, params[0])
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