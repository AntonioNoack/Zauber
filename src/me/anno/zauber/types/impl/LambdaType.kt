package me.anno.zauber.types.impl

import me.anno.zauber.types.LambdaParameter
import me.anno.zauber.types.Type

/**
 * Lambda type, with always known parameter types...
 * Self.(A,B,C) -> R
 *
 * Int::plus can be understood as (Int,Int) -> Int, and Int.(Int) -> Int
 * */
class LambdaType(
    val selfType: Type?,
    val parameters: List<LambdaParameter>,
    val returnType: Type
) : Type() {

    override fun toStringImpl(depth: Int): String {
        val scopeType = if (selfType != null) "self=${selfType.toString(depth)} | " else ""
        return "($scopeType(${
            parameters.joinToString(", ") {
                if (it.name != null) "${it.name}=${it.type.toString(depth)}"
                else it.type.toString(depth)
            }
        }) -> ${returnType.toString(depth)})"
    }

    override fun equals(other: Any?): Boolean {
        return other is LambdaType &&
                parameters == other.parameters &&
                returnType == other.returnType
    }

    override fun hashCode(): Int {
        return parameters.hashCode() * 31 + returnType.hashCode()
    }
}