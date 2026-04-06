package me.anno.zauber.types

import me.anno.zauber.ast.rich.NamedParameter
import me.anno.zauber.ast.rich.expression.*
import me.anno.zauber.ast.rich.expression.unresolved.NamedCallExpression

object BooleanUtils {
    fun Expression.not(): Expression {
        // undo a not
        if (this is NamedCallExpression &&
            name == "not" &&
            self.resolvedType == Types.Boolean
        ) {
            return self
        }

        if (this is CheckEqualsOp) {
            return CheckEqualsOp(left, right, byPointer, !negated, null, scope, origin)
        }

        resolvedType = Types.Boolean
        return NamedCallExpression(
            this, "not", scope, origin
        )
    }

    fun Expression.and(other: Expression): Expression {
        resolvedType = Types.Boolean
        other.resolvedType = Types.Boolean
        return NamedCallExpression(
            this, "and", emptyList(), emptyList(),
            listOf(NamedParameter(null, other)),
            scope, origin
        ).apply {
            resolvedType = Types.Boolean
        }
    }
}