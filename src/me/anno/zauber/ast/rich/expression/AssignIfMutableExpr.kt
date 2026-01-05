package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.ValueParameterImpl
import me.anno.zauber.typeresolution.members.MethodResolver
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

/**
 * this.name [+=, *=, /=, ...] right
 * */
class AssignIfMutableExpr(val left: Expression, val symbol: String, val right: Expression) :
    Expression(left.scope, right.origin) {

    override fun toStringImpl(depth: Int): String {
        return "${left.toString(depth)} $symbol ${right.toString(depth)}"
    }

    private fun findField(expr: Expression, context: ResolutionContext): Field? {
        return when (expr) {
            is FieldExpression -> expr.field
            is UnresolvedFieldExpression -> {
                val field = expr.resolveField(context)!!
                field.resolved
            }
            else -> throw NotImplementedError("Is ${expr.javaClass.simpleName} a mutable left side?")
        }
    }

    private fun getMethodOrNull(context: ResolutionContext, name: String, rightType: Type): Method? {
        val resolved = MethodResolver.resolveMethod(
            context, name, null,
            listOf(ValueParameterImpl(null, rightType, false))
        )
        return resolved?.resolved
    }

    class ResolveResult(
        val needsFieldAssignment: Field?,
        val neededCall: Method
    )

    fun resolveMethod(context: ResolutionContext): ResolveResult {
        val field = findField(left, context)
        val isFieldMutable = field?.isMutable == true
        val leftType = TypeResolution.resolveType(context, left)
        val rightType = TypeResolution.resolveType(context, right)
        val leftContext = context.withSelfType(leftType)
        val plusMethod = getMethodOrNull(leftContext, "plus", rightType)
        val plusAssignMethod = getMethodOrNull(leftContext, "plusAssign", rightType)
        check(plusMethod != null || plusAssignMethod != null) {
            "Either a plus or a plusAssign method must be declared on $leftType"
        }

        val isContentMutable = plusAssignMethod != null
        check(isFieldMutable != isContentMutable) {
            "Either field or content must be mutable, plus? $plusMethod, plusAssign? $plusAssignMethod, fieldMutable? $isFieldMutable"
        }

        return if (isContentMutable) {
            ResolveResult(null, plusAssignMethod)
        } else {
            check(plusMethod != null) { "Cannot resolve $leftType.plus($rightType)" }
            ResolveResult(field, plusMethod)
        }
    }

    override fun resolveType(context: ResolutionContext): Type {
        resolveMethod(context) // to crash if something is wrong; not strictly needed
        return exprHasNoType(context)
    }

    override fun hasLambdaOrUnknownGenericsType(): Boolean = false // UnitType

    override fun clone(scope: Scope): Expression = AssignIfMutableExpr(left.clone(scope), symbol, right.clone(scope))
}