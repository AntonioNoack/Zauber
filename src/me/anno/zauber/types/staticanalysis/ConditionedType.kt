package me.anno.zauber.types.staticanalysis

import me.anno.zauber.types.Type

/**
 * todo for static code analysis, we will want these types everywhere...
 * */
class ConditionedType(val base: Type, val conditions: List<TypeCondition>): Type() {

    override fun toStringImpl(depth: Int): String {
        TODO("Not yet implemented")
    }
}