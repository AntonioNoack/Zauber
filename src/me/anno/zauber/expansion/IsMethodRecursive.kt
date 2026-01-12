package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.MethodLike

object IsMethodRecursive : FunctionColoring<MethodLike>(true) {
    override fun getDependencies(func: MethodLike): List<MethodLike> {
        TODO("Not yet implemented")
    }

    override fun isColoredBySelf(func: MethodLike): Boolean = false
}