package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.MethodLike
// todo we need to collect any yielded types and any thrown types
/*
object IsMethodYielding : MethodColoring<MethodLike>(true) {
    override fun getDependencies(func: MethodLike): List<MethodLike> {
        // todo check for any throw/yield instructions
        TODO("Not yet implemented")
    }

    override fun isColoredBySelf(func: MethodLike): Boolean = false
}*/