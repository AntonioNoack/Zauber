package me.anno.zauber.types.typeresolution

import me.anno.utils.ResolutionUtils.ctr
import me.anno.utils.ResolutionUtils.firstChild
import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.parameter.Parameter
import me.anno.zauber.ast.rich.parser.ZauberASTBuilder
import me.anno.zauber.ast.rich.parser.ZauberASTClassScanner.Companion.scanClasses
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.unresolved.CallExpression
import me.anno.zauber.ast.rich.expression.unresolved.FieldExpression
import me.anno.zauber.ast.rich.expression.unresolved.FieldResolvable
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.scope.lazy.LazyExpression
import me.anno.zauber.tokenizer.ZauberTokenizer
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.impl.LambdaType
import org.junit.jupiter.api.Test

class TestInlineLambdaParameterIsImmediatelyFound {
    /**
     * This is not strictly necessary, but it simplifies implementing inline functions a lot.
     * */
    @Test
    fun testLambdaCallIsImmediatelyResolvedToParameter() {
        val testScopeName = "test${ctr++}"
        val tokens = ZauberTokenizer(
            """
            package $testScopeName
            
            inline fun tested(runnable: (Int) -> Unit) {
                runnable(15)
            }
        """.trimIndent(), "Test.zbr"
        ).tokenize()
        scanClasses(tokens)
        ZauberASTBuilder(tokens, root).readFileLevel()
        val testScope = root.children.first { it.name == testScopeName }
        val method = testScope[ScopeInitType.AFTER_DISCOVERY].firstChild(ScopeType.METHOD)
        val expr = findCallExpression(method.selfAsMethod!!.body!!)
        println("${expr.self}, ${expr.self.javaClass.simpleName}")
        val field = (expr.self as FieldResolvable)
            .resolveField(ResolutionContext.minimal)!!.resolved
        check(field.name == "runnable")
        check(field.byParameter is Parameter)
        check(field.valueType is LambdaType)
    }

    private fun findCallExpression(expr: Expression): CallExpression {
        var expr = expr
        while (true) {
            expr = when (expr) {
                is LazyExpression -> expr.value
                is ExpressionList -> expr.list[0]
                is CallExpression -> return expr
                else -> throw NotImplementedError()
            }
        }
    }

}