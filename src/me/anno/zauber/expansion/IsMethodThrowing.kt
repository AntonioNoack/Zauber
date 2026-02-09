package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.controlflow.ThrowExpression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NothingType
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes
import me.anno.zauber.types.specialization.MethodSpecialization

object IsMethodThrowing : MethodColoring<Type>() {

    override fun getSelfColor(method: MethodSpecialization): Type {
        val body = method.method.getSpecializedBody(method.specialization) ?: return NothingType
        val thrownTypes = ArrayList<Type>()
        val context = ResolutionContext(method.method.selfType, method.specialization, false, null)
        body.forEachExpressionRecursively { expr ->
            if (expr is ThrowExpression) {
                val type = TypeResolution.resolveType(context, expr.value)
                thrownTypes.add(type)
            }
        }
        return unionTypes(thrownTypes)
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