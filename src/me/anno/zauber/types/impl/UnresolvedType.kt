package me.anno.zauber.types.impl

import me.anno.zauber.ast.rich.ZauberASTBuilderBase.Companion.resolveTypeByName
import me.anno.zauber.types.Import
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class UnresolvedType(
    val className: String, val typeParameters: List<Type>?,
    val scope: Scope, val imports: List<Import>
) : Type() {

    override fun toStringImpl(depth: Int): String {
        return "$className?<$typeParameters>"
    }
}