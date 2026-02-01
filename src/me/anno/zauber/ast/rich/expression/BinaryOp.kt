package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.ASTBuilderBase
import me.anno.zauber.ast.rich.NamedParameter
import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.ast.rich.expression.resolved.ThisExpression
import me.anno.zauber.ast.rich.expression.unresolved.*
import me.anno.zauber.ast.rich.expression.unresolved.AssignIfMutableExpr.Companion.plusAssignName
import me.anno.zauber.ast.rich.expression.unresolved.AssignIfMutableExpr.Companion.plusName
import me.anno.zauber.logging.LogManager
import me.anno.zauber.types.BooleanUtils.not
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

private val LOGGER = LogManager.getLogger("BinaryOp")

@Suppress("IntroduceWhenSubject") // this feature is experimental, why is it recommended???
fun ASTBuilderBase.binaryOp(
    scope: Scope, left: Expression, symbol: String, right: Expression,
    origin: Int = left.origin
): Expression {
    return when (symbol) {
        "<=" -> CompareOp(left, right, CompareType.LESS_EQUALS)
        "<" -> CompareOp(left, right, CompareType.LESS)
        ">=" -> CompareOp(left, right, CompareType.GREATER_EQUALS)
        ">" -> CompareOp(left, right, CompareType.GREATER)
        "==" -> CheckEqualsOp(left, right, byPointer = false, negated = false, scope, origin)
        "!=" -> CheckEqualsOp(left, right, byPointer = false, negated = true, scope, origin)
        "===" -> CheckEqualsOp(left, right, byPointer = true, negated = false, scope, origin)
        "!==" -> CheckEqualsOp(left, right, byPointer = true, negated = true, scope, origin)
        "&&", "||" -> throw IllegalStateException("&& and || should be handled separately")
        "::" -> {

            fun getBase(): Scope = when {
                // left is VariableExpression -> scope.resolveType(left.name, this) as Scope
                left is ThisExpression -> left.label
                else -> throw NotImplementedError("GetBase($left::$right at ${tokens.err(i)})")
            }

            val leftIsType = left is MemberNameExpression && left.name[0].isUpperCase() ||
                    left is ThisExpression

            when {
                leftIsType && right is MemberNameExpression -> {
                    GetMethodFromTypeExpression(getBase(), right.name, right.scope, right.origin)
                }
                right is MemberNameExpression -> {
                    GetMethodFromValueExpression(left, right.name, right.origin)
                }
                right is UnresolvedFieldExpression -> {
                    GetMethodFromValueExpression(left, right.name, right.origin)
                }
                else -> throw NotImplementedError(
                    "WhichType? $left::$right, " +
                            "(${left.javaClass.simpleName})::(${right.javaClass.simpleName}), " +
                            "at ${resolveOrigin(origin)}"
                )
            }
        }
        "=" -> AssignmentExpression(left, right)
        "." -> {
            val typeParameters: List<Type> = emptyList()
            when (right) {
                is NamedCallExpression -> {
                    // todo ideally, this would be handled by association-order...
                    // reorder stack from left to right
                    val leftAndMiddle = DotExpression(left, typeParameters, right.self, left.scope, left.origin)
                    NamedCallExpression(
                        leftAndMiddle, right.name, right.nameAsImport,
                        right.typeParameters, right.valueParameters,
                        right.scope, right.origin
                    )
                }
                is DotExpression -> {
                    // todo ideally, this would be handled by association-order...
                    // reorder stack from left to right
                    val leftAndMiddle = DotExpression(left, typeParameters, right.left, left.scope, left.origin)
                    DotExpression(leftAndMiddle, right.typeParameters, right.right, right.scope, right.origin)
                }
                else -> DotExpression(left, typeParameters, right, right.scope, right.origin)
            }
        }
        "in", "!in" -> {
            // swap the order of the arguments without changing their calculation order:
            // save left as temporary, then put into right side
            val leftTmp = currPackage.createImmutableField(left)
            val methodName = lookupBinaryOp("in", origin)
            val param = NamedParameter(null, FieldExpression(leftTmp, scope, origin))
            val expr = NamedCallExpression(
                right, methodName, nameAsImport(methodName),
                emptyList(), listOf(param),
                right.scope, right.origin
            )
            if (symbol == "in") expr else expr.not()
        }
        else -> {
            if (symbol.endsWith('=')) {
                // todo oh no, to know whether this is mutable or not,
                //  we have to know all types, because left may be really complicated,
                //  e.g. a["5",3].x()() += 17
                // todo add correct plusAsImport
                AssignIfMutableExpr(
                    left, symbol,
                    nameAsImport(plusName(symbol)),
                    nameAsImport(plusAssignName(symbol)), right
                )
            } else if (symbol.startsWith("!")) {
                val methodName = lookupBinaryOp(symbol.substring(1), origin)
                val param = NamedParameter(null, right)
                NamedCallExpression(
                    left, methodName, nameAsImport(methodName),
                    null, listOf(param),
                    right.scope, right.origin
                ).not()
            } else {
                val methodName = lookupBinaryOp(symbol, origin)
                val param = NamedParameter(null, right)
                NamedCallExpression(
                    left, methodName, nameAsImport(methodName),
                    null, listOf(param),
                    right.scope, right.origin
                )
            }
        }
    }
}

fun lookupBinaryOp(symbol: String, origin: Int): String {
    return when (symbol) {
        "+" -> "plus"
        "-" -> "minus"
        "*" -> "times"
        "/" -> "div"
        "%" -> "rem"
        ".." -> "rangeTo"
        "..<" -> "until"
        "in" -> "contains"
        else -> {
            LOGGER.warn("Unknown binary op: $symbol at ${resolveOrigin(origin)}")
            symbol
        }
    }
}