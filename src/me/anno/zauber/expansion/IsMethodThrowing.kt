package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes

// todo we need to collect any yielded types and any thrown types

object IsMethodThrowing : MethodColoring<MethodLike, Type>() {

    override fun getDependencies(func: MethodLike): List<MethodLike> {
        // todo check for any throw/yield instructions
        TODO("Not yet implemented")
    }

    override fun getSelfColor(func: MethodLike): Type {
        // todo iterate over the body
        TODO("Not yet implemented")
    }

    override fun mergeColors(
        self: Type,
        colors: List<Type>,
        isRecursive: Boolean
    ): Type {
        // recursive doesn't matter
        return unionTypes(colors, self)
    }
}