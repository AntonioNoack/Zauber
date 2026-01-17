package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
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
        check(ifBranch.scope != elseBranch?.scope) {
            "IfBranch and ElseBranch must have different scopes. ${ifBranch.scope}, " +
                    "at ${resolveOrigin(condition.origin)}"
        }
        check(
            ifBranch.scope != condition.scope ||
                    ifBranch is SpecialValueExpression
        ) {
            "If and condition somehow have the same scope: ${condition.scope.pathStr}, " +
                    "at ${resolveOrigin(condition.origin)}"
        }
        check(
            elseBranch?.scope != condition.scope ||
                    elseBranch is SpecialValueExpression
        ) {
            "Else and condition somehow have the same scope: ${condition.scope.pathStr}, " +
                    "at ${resolveOrigin(condition.origin)}"
        }

        if (addToScope) {
            ifBranch.scope.addCondition(condition)
            elseBranch?.scope?.addCondition(condition.not())
        }
    }

    override fun resolveType(context: ResolutionContext): Type {
        return if (elseBranch == null) {
            exprHasNoType(context)
        } else {
            // targetLambdaType stays the same
            val ifType = TypeResolution.resolveType(context, ifBranch)
            val elseType = TypeResolution.resolveType(context, elseBranch)
            unionTypes(ifType, elseType)
        }
    }

    override fun clone(scope: Scope): Expression = IfElseBranch(
        condition.clone(scope),
        ifBranch.clone(ifBranch.scope),
        elseBranch?.clone(elseBranch.scope),
        false
    )

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean {
        return elseBranch != null && // if else is undefined, this has no return type
                (ifBranch.hasLambdaOrUnknownGenericsType(context) ||
                        elseBranch.hasLambdaOrUnknownGenericsType(context))
    }

    override fun needsBackingField(methodScope: Scope): Boolean {
        return condition.needsBackingField(methodScope) ||
                ifBranch.needsBackingField(methodScope) ||
                elseBranch?.needsBackingField(methodScope) == true
    }

    // todo if-else-branch can enforce a condition: if only one branch returns
    override fun splitsScope(): Boolean = false

    override fun isResolved(): Boolean = condition.isResolved() &&
            ifBranch.isResolved() &&
            (elseBranch == null || elseBranch.isResolved())

    override fun resolveImpl(context: ResolutionContext): Expression {
        return IfElseBranch(
            condition.resolve(context),
            ifBranch.resolve(context),
            elseBranch?.resolve(context)
        )
    }

    override fun toStringImpl(depth: Int): String {
        return if (elseBranch == null) {
            "if(${condition.toString(depth)}) { ${ifBranch.toString(depth)} }"
        } else {
            "if(${condition.toString(depth)}) { ${ifBranch.toString(depth)} } else { ${elseBranch.toString(depth)} }"
        }
    }

}