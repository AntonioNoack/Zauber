package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.ast.rich.controlflow.ThrowExpression
import me.anno.zauber.ast.rich.controlflow.YieldExpression
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NothingType
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes

object IsMethodYielding : MethodColoring<Type>() {

    override fun getSelfColor(method: MethodLike): Type {
        // todo only the specialized body should be processed...
        val body = method.body ?: return NothingType
        val yieldedTypes = ArrayList<Type>()
        val context = ResolutionContext(method.selfType, false, null)
        body.forEachExpressionRecursively { expr ->
            if (expr is YieldExpression) {
                val type = TypeResolution.resolveType(context, expr.value)
                yieldedTypes.add(type)
            }
        }
        return unionTypes(yieldedTypes)
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