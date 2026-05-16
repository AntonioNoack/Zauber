package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.controlflow.YieldExpression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.arithmetic.UnionType.Companion.unionTypes
import me.anno.zauber.types.Specialization

object IsMethodYielding : MethodColoring<Type>() {

    override fun getSelfColor(key: Specialization): Type {
        val body = key.method.getSpecializedBody(key) ?: return Types.Nothing
        val yieldedTypes = ArrayList<Type>()
        val context = ResolutionContext(key.method.selfType, false, null)
        body.forEachExpressionRecursively { expr ->
            if (expr is YieldExpression) {
                val type = TypeResolution.resolveType(context, expr.value)
                yieldedTypes.add(type)
            }
        }
        return unionTypes(yieldedTypes)
    }

    override fun mergeColors(
        key: Specialization, self: Type,
        colors: List<Type>, isRecursive: Boolean
    ): Type {
        // recursive doesn't matter
        return unionTypes(colors, self)
    }
}