package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.MethodLike

object IsMethodRecursive : BoolMethodColoring(isRecursionColored = true) {
    override fun isColoredBySelf(method: MethodLike): Boolean = false
}