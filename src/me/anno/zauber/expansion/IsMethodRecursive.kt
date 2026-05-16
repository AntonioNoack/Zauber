package me.anno.zauber.expansion

import me.anno.zauber.types.Specialization

object IsMethodRecursive : BoolMethodColoring(isRecursionColored = true) {
    override fun isColoredBySelf(method: Specialization): Boolean = false
}