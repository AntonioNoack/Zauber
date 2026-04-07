package me.anno.zauber.scope.lazy

import me.anno.zauber.Compile.root
import me.anno.zauber.ZauberLanguage
import me.anno.zauber.ast.rich.ZauberASTBuilder
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.scope.Scope
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Import
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.GenericType

class LazyExpression(
    val tokens: TokenSubList,
    val isBody: Boolean,
    scope: Scope, origin: Int,
    val imports: List<Import>,
    val generics: HashMap<String, GenericType>
) : Expression(scope, origin) {

    companion object {
        fun eval(
            tokens: TokenList,
            i0: Int, i1: Int,
            isBody: Boolean,
            scope: Scope,
            imports: List<Import>,
            generics: HashMap<String, GenericType>
        ): Expression {
            val tmp = ZauberASTBuilder(tokens, root, ZauberLanguage.ZAUBER)
            tmp.imports.addAll(imports)
            tmp.genericParams.add(generics)

            tmp.i = i0
            tmp.currPackage = scope
            return tmp.push(i1) {
                if (isBody) tmp.readMethodBody()
                else tmp.readExpression()
            }
        }
    }

    val value by lazy {
        val tokens = tokens
        eval(tokens.tokens, tokens.i0, tokens.i1, isBody, scope, imports, generics)
    }

    override fun resolveReturnType(context: ResolutionContext): Type = value.resolveReturnType(context)
    override fun clone(scope: Scope): Expression = value.clone(scope)
    override fun toStringImpl(depth: Int): String = "LazyExpression(${tokens.extractString()})"

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