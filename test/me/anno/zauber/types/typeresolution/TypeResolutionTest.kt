package me.anno.zauber.types.typeresolution

import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.rich.ZauberASTClassScanner.Companion.scanClasses
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.expansion.DefaultParameterExpansion.createDefaultParameterFunctions
import me.anno.zauber.expansion.OverriddenMethods.resolveOverrides
import me.anno.zauber.scope.Scope
import me.anno.zauber.tokenizer.ZauberTokenizer
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.impl.UnionType
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes
import me.anno.utils.ResetThreadLocal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypeResolutionTest {

    companion object {
        var ctr = 0

        fun testTypeResolution0(code: String, reset: Boolean): Scope {

            // clean slate
            if (reset) ResetThreadLocal.reset()

            val testScopeName = "test${ctr++}"
            val tokens = ZauberTokenizer(
                """
            package $testScopeName
            
            $code
        """.trimIndent(), "Test.zbr"
            ).tokenize()
            scanClasses(tokens)
            createDefaultParameterFunctions(root)
            val testScope = root.children.first { it.name == testScopeName }.scope
            resolveOverrides(testScope)
            return testScope
        }

        fun testTypeResolutionGetField(code: String, reset: Boolean): Field {
            return testTypeResolution0(code, reset).fields.first { it.name == "tested" }
        }

        fun testTypeResolution(code: String, reset: Boolean = false): Type {
            val field = testTypeResolutionGetField(code, reset)
            val context = ResolutionContext(null, false, null)
            return field.resolveValueType(context)
        }

        fun testMethodBodyResolution(code: String): List<Type> {
            val testScope = testTypeResolution0(code, reset = true).scope
            val method = testScope.methods.first { it.name == "tested" }
            val types = ArrayList<Type>()
            fun scan(expr: Expression) {
                if (expr is ExpressionList) {
                    for (exprI in expr.list) {
                        scan(exprI)
                    }
                } else {
                    val context = ResolutionContext(
                        method.selfType,
                        true,
                        null,
                        emptyMap()
                    )
                    val type = TypeResolution.resolveType(context, expr)
                    types.add(type)
                }
            }
            scan(method.body!!)
            return types
        }

        fun defineListParameters() {
            Types.List.clazz
        }
    }

    @Test
    fun testConstants() {
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = true")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = false")
        )
        assertEquals(
            NullType,
            testTypeResolution("val tested = null")
        )
        assertEquals(
            Types.Int,
            testTypeResolution("val tested = 0")
        )
        assertEquals(
            Types.Long,
            testTypeResolution("val tested = 0L")
        )
        assertEquals(
            Types.Float,
            testTypeResolution("val tested = 0f")
        )
        assertEquals(
            Types.Float,
            testTypeResolution("val tested = 0.0f")
        )
        assertEquals(
            Types.Double,
            testTypeResolution("val tested = 0d")
        )
        assertEquals(
            Types.Double,
            testTypeResolution("val tested = 0.0")
        )
        assertEquals(
            Types.Double,
            testTypeResolution("val tested = 1e3")
        )
        assertEquals(
            Types.Char,
            testTypeResolution("val tested = ' '")
        )
        assertEquals(
            Types.String,
            testTypeResolution("val tested = \"Test 123\"")
        )
    }

    @Test
    fun testNullableTypes() {
        assertEquals(
            UnionType(listOf(ClassType(Types.Boolean.clazz, null), NullType)),
            testTypeResolution("val tested: Boolean?")
        )
    }

    @Test
    fun testConstructorWithParameter() {
        val code = """
            val tested = IntArray(5)
            
            package zauber
            class Array<V>(val size: Int)
            typealias IntArray = Array<Int>
        """.trimIndent()
        assertEquals(
            Types.Array.withTypeParameter(Types.Int),
            testTypeResolution(code)
        )
    }

    @Test
    fun testGetOperator() {
        val type =
            testTypeResolution(
                """
            class Node(val value: Int)
            val x: Node
            val tested = x.value
        """.trimIndent()
            )
        assertEquals(Types.Int, type)
    }

    @Test
    fun testIfNullOperator() {
        val type =
            testTypeResolution(
                """
            val x: Int?
            val tested = x ?: 0f
        """.trimIndent()
            )
        assertEquals(unionTypes(Types.Int, Types.Float), type)
    }

    @Test
    fun testNullableGetOperator() {
        val type =
            testTypeResolution(
                """
            class Node(val parent: Node?, val value: Int)
            val x: Node?
            val tested = x?.parent?.value
        """.trimIndent()
            )
        assertEquals(unionTypes(Types.Int, NullType), type)
    }

    @Test
    fun testCompareOperators() {
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0 < 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0 <= 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0 > 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0 >= 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0 == 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0 != 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0 === 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0 !== 1")
        )
    }

    @Test
    fun testCompareOperatorsMixed() {
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0f < 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0f <= 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0f > 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0f >= 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0f == 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0f != 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0f === 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0f !== 1")
        )
    }

    @Test
    fun testValueType() {
        val field = testTypeResolutionGetField("value val tested = \"\"", true)
        check(field.flags.hasFlag(Flags.VALUE))
    }
}