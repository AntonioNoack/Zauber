package me.anno.zauber.scope.lazy

import me.anno.zauber.Compile.root
import me.anno.zauber.ZauberLanguage
import me.anno.zauber.ast.rich.ZauberASTBuilder
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

class LazyExpression(
    val tokens: TokenSubList,
    val isBody: Boolean,
    scope: Scope, origin: Int,
) : Expression(scope, origin) {

    val value by lazy {
        val tmp = ZauberASTBuilder(tokens.tokens, root, ZauberLanguage.ZAUBER)
        tmp.imports.addAll(tokens.imports)

        tmp.i = tokens.i0
        tmp.currPackage = scope
        tmp.tokens.push(tokens.i1) {
            if (isBody) tmp.readMethodBody()
            else tmp.readExpression()
        }
    }

    override fun resolveReturnType(context: ResolutionContext): Type = value.resolveReturnType(context)
    override fun clone(scope: Scope): Expression = value.clone(scope)
    override fun toStringImpl(depth: Int): String = value.toStringImpl(depth)
    override fun resolveImpl(context: ResolutionContext): Expression = value.resolveImpl(context)

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean =
        value.hasLambdaOrUnknownGenericsType(context)

    override fun needsBackingField(methodScope: Scope): Boolean = value.needsBackingField(methodScope)
    override fun splitsScope(): Boolean = value.splitsScope()
    override fun isResolved(): Boolean = value.isResolved()

    override fun forEachExpression(callback: (Expression) -> Unit) {
        value.forEachExpression(callback)
    }


}