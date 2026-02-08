package me.anno.zauber.expansion

import me.anno.zauber.types.specialization.MethodSpecialization

object IsMethodRecursive : BoolMethodColoring(isRecursionColored = true) {
    override fun isColoredBySelf(method: MethodSpecialization): Boolean = false
}