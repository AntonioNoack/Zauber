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
    scope: Scope, origin: Int,
) : Expression(scope, origin) {

    private val self by lazy {
        val tmp = ZauberASTBuilder(tokens.tokens, root, ZauberLanguage.ZAUBER)
        tmp.imports.addAll(tokens.imports)

        tmp.i = tokens.i0
        tmp.tokens.size = tokens.i1
        tmp.readExpression()
    }

    override fun resolveReturnType(context: ResolutionContext): Type = self.resolveReturnType(context)
    override fun clone(scope: Scope): Expression = self.clone(scope)
    override fun toStringImpl(depth: Int): String = self.toStringImpl(depth)
    override fun resolveImpl(context: ResolutionContext): Expression = self.resolveImpl(context)

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean =
        self.hasLambdaOrUnknownGenericsType(context)

    override fun needsBackingField(methodScope: Scope): Boolean = self.needsBackingField(methodScope)
    override fun splitsScope(): Boolean = self.splitsScope()
    override fun isResolved(): Boolean = self.isResolved()

    override fun forEachExpression(callback: (Expression) -> Unit) {
        self.forEachExpression(callback)
    }


}