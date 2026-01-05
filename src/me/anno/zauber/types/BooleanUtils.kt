package me.anno.zauber.types

import me.anno.zauber.ast.rich.NamedParameter
import me.anno.zauber.ast.rich.expression.*
import me.anno.zauber.types.Types.BooleanType

object BooleanUtils {
    fun Expression.not(): Expression {
        // undo a not
        if (this is NamedCallExpression &&
            name == "not" &&
            base.resolvedType == BooleanType
        ) {
            return base
        }

        if (this is CheckEqualsOp) {
            return CheckEqualsOp(left, right, byPointer, !negated, scope, origin)
        }

        resolvedType = BooleanType
        return NamedCallExpression(
            this, "not", emptyList(),
            emptyList(), scope, origin
        )
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