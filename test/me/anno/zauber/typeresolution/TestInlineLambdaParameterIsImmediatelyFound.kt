package me.anno.zauber.typeresolution

import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.ASTClassScanner.Companion.collectNamedClasses
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.ZauberASTBuilder
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.unresolved.CallExpression
import me.anno.zauber.ast.rich.expression.unresolved.FieldExpression
import me.anno.zauber.resolution.ResolutionUtils.firstChild
import me.anno.zauber.tokenizer.ZauberTokenizer
import me.anno.zauber.typeresolution.TypeResolutionTest.Companion.ctr
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.impl.LambdaType
import org.junit.jupiter.api.Test

class TestInlineLambdaParameterIsImmediatelyFound {
    @Test
    fun testExprOrNull() {
        val testScopeName = "test${ctr++}"
        val tokens = ZauberTokenizer(
            """
            package $testScopeName
            
            inline fun tested(runnable: (Int) -> Unit) {
                runnable(15)
            }
        """.trimIndent(), "?"
        ).tokenize()
        collectNamedClasses(tokens)
        ZauberASTBuilder(tokens, root).readFileLevel()
        val testScope = root.children.first { it.name == testScopeName }
        val method = testScope.firstChild(ScopeType.METHOD)
        val body = method.selfAsMethod!!.body as ExpressionList
        val expr = body.list[0] as CallExpression
        println("${expr.base}, ${expr.base.javaClass.simpleName}")
        val field = (expr.base as FieldExpression).field
        check(field.name == "runnable")
        check(field.byParameter is Parameter)
        check(field.valueType is LambdaType)
    }

}