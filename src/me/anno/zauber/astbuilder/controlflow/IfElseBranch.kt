package me.anno.zauber.astbuilder.controlflow

import me.anno.zauber.astbuilder.expression.Expression
import me.anno.zauber.astbuilder.expression.constants.SpecialValueExpression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.BooleanUtils.not
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes

class IfElseBranch(
    val condition: Expression, val ifBranch: Expression, val elseBranch: Expression?,
    addToScope: Boolean = true
) : Expression(condition.scope, condition.origin) {

    init {
        check(ifBranch.scope != elseBranch?.scope)
        check(
            ifBranch.scope != condition.scope ||
                    ifBranch is SpecialValueExpression
        ) {
            "If and condition somehow have the same scope: ${condition.scope.pathStr}"
        }
        check(
            elseBranch?.scope != condition.scope ||
                    elseBranch is SpecialValueExpression
        ) {
            "Else and condition somehow have the same scope: ${condition.scope.pathStr}"
        }

        if (addToScope) {
            ifBranch.scope.addCondition(condition)
            elseBranch?.scope?.addCondition(condition.not())
        }
    }

    override fun forEachExpr(callback: (Expression) -> Unit) {
        callback(condition)
        callback(ifBranch)
        if (elseBranch != null) callback(elseBranch)
    }

    override fun resolveType(context: ResolutionContext): Type {
        return if (elseBranch == null) {
            exprHasNoType(context)
        } else {
            // targetLambdaType stays the same
            val ifType = TypeResolution.resolveType(context.withCodeScope(ifBranch.scope), ifBranch)
            val elseType = TypeResolution.resolveType(context.withCodeScope(elseBranch.scope), elseBranch)
            unionTypes(ifType, elseType)
        }
    }

    override fun clone(scope: Scope): Expression = IfElseBranch(
        condition.clone(scope),
        ifBranch.clone(ifBranch.scope),
        elseBranch?.clone(elseBranch.scope),
        false
    )

    override fun hasLambdaOrUnknownGenericsType(): Boolean {
        println("Checking branch for unknown generics type: ${ifBranch.hasLambdaOrUnknownGenericsType()} || ${elseBranch?.hasLambdaOrUnknownGenericsType()}")
        return elseBranch != null && // if else is undefined, this has no return type
                (ifBranch.hasLambdaOrUnknownGenericsType() || elseBranch.hasLambdaOrUnknownGenericsType())
    }

    override fun toStringImpl(depth: Int): String {
        return if (elseBranch == null) {
            "if(${condition.toString(depth)}) { ${ifBranch.toString(depth)} }"
        } else {
            "if(${condition.toString(depth)}) { ${ifBranch.toString(depth)} } else { ${elseBranch.toString(depth)} }"
        }
    }

}