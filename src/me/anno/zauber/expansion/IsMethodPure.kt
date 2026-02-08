package me.anno.zauber.expansion

import me.anno.zauber.types.specialization.MethodSpecialization

/**
 * a pure function is one that neither yields, throws, non-stack-allocates, nor has any other side effects
 * */
object IsMethodPure : BoolMethodColoring(true) {
    override fun isColoredBySelf(method: MethodSpecialization): Boolean {
        TODO("Not yet implemented")
    }
}