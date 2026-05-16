package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.controlflow.ThrowExpression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.arithmetic.UnionType.Companion.unionTypes
import me.anno.zauber.types.Specialization

object IsMethodThrowing : MethodColoring<Type>() {

    override fun getSelfColor(key: Specialization): Type {
        val body = key.method.getSpecializedBody(key) ?: return Types.Nothing
        val thrownTypes = ArrayList<Type>()
        val context = ResolutionContext(key.method.selfType, key, false, null)
        body.forEachExpressionRecursively { expr ->
            if (expr is ThrowExpression) {
                val type = TypeResolution.resolveType(context, expr.value)
                thrownTypes.add(type)
            }
        }
        return unionTypes(thrownTypes)
    }

    override fun mergeColors(
        key: Specialization, self: Type,
        colors: List<Type>, isRecursive: Boolean
    ): Type {
        // recursive doesn't matter
        return unionTypes(colors, self)
    }
}