package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.MethodLike

object IsMethodYielding : FunctionColoring<MethodLike>(true) {
    override fun getDependencies(func: MethodLike): List<MethodLike> {
        // todo check for any throw/yield instructions
        TODO("Not yet implemented")
    }

    override fun isColoredBySelf(func: MethodLike): Boolean = false
}