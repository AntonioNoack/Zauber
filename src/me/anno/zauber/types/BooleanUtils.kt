package me.anno.zauber.types

import me.anno.zauber.ast.rich.NamedParameter
import me.anno.zauber.ast.rich.expression.*
import me.anno.zauber.types.Types.BooleanType

object BooleanUtils {
    fun Expression.not(): Expression {
        // undo a not
        if (this is PrefixExpression && this.type == PrefixType.NOT) {
            return base
        }

        if (this is IsInstanceOfExpr) {
            return IsInstanceOfExpr(
                left, right, !negated,
                scope, origin
            )
        }

        if (this is CheckEqualsOp) {
            return CheckEqualsOp(left, right, byPointer, !negated, scope, origin)
        }

        resolvedType = BooleanType
        return PrefixExpression(PrefixType.NOT, origin, this).apply {
            resolvedType = BooleanType
        }
    }

    fun Expression.and(other: Expression): Expression {
        resolvedType = BooleanType
        other.resolvedType = BooleanType
        return NamedCallExpression(
            this, "and", emptyList(),
            listOf(NamedParameter(null, other)),
            scope, origin
        ).apply {
            resolvedType = BooleanType
        }
    }
}