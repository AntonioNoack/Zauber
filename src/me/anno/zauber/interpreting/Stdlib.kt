package me.anno.zauber.interpreting

import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.impl.ClassType

object Stdlib {

    fun Runtime.registerBinaryMethod(type: ClassType, name: String, calc: (Instance, Instance) -> Instance) {
        register(type.clazz, name, listOf(type)) { _, self, params ->
            calc(self!!, params[0])
        }
    }

    fun registerPrintln(rt: Runtime) {
        rt.register(langScope, "println", listOf(StringType)) { rt, _, params ->
            val content = rt.castToString(params[0])
            rt.printed += content
            println(content)
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