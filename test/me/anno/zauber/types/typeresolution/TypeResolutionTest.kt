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
import me.anno.zauber.utils.ResetThreadLocal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypeResolutionTest {

    companion object {
        var ctr = 0

        fun testTypeResolution0(code: String): Scope {

            // clean slate
            ResetThreadLocal.reset()

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
            // resolveTypesAndNames(testScope)
            return testScope
        }

        fun testTypeResolutionGetField(code: String): Field {
            return testTypeResolution0(code).fields.first { it.name == "tested" }
        }

        fun testTypeResolution(code: String): Type {
            val field = testTypeResolutionGetField(code)
            val context = ResolutionContext(null, false, null)
            return field.resolveValueType(context)
        }

        fun testMethodBodyResolution(code: String): List<Type> {
            val testScope = testTypeResolution0(code).scope
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
            Types.ListType.clazz
        }
    }

    @Test
    fun testConstants() {
        assertEquals(
            Types.BooleanType,
            testTypeResolution("val tested = true")
        )
        assertEquals(
            Types.BooleanType,
            testTypeResolution("val tested = false")
        )
        assertEquals(
            NullType,
            testTypeResolution("val tested = null")
        )
        assertEquals(
            Types.IntType,
            testTypeResolution("val tested = 0")
        )
        assertEquals(
            Types.LongType,
            testTypeResolution("val tested = 0L")
        )
        assertEquals(
            Types.FloatType,
            testTypeResolution("val tested = 0f")
        )
        assertEquals(
            Types.FloatType,
            testTypeResolution("val tested = 0.0f")
        )
        assertEquals(
            Types.DoubleType,
            testTypeResolution("val tested = 0d")
        )
        assertEquals(
            Types.DoubleType,
            testTypeResolution("val tested = 0.0")
        )
        assertEquals(
            Types.DoubleType,
            testTypeResolution("val tested = 1e3")
        )
        assertEquals(
            Types.CharType,
            testTypeResolution("val tested = ' '")
        )
        assertEquals(
            Types.StringType,
            testTypeResolution("val tested = \"Test 123\"")
        )
    }

    @Test
    fun testNullableTypes() {
        assertEquals(
            UnionType(listOf(ClassType(Types.BooleanType.clazz, null), NullType)),
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
            Types.ArrayType.withTypeParameter(Types.IntType),
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
        assertEquals(Types.IntType, type)
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
        assertEquals(unionTypes(Types.IntType, Types.FloatType), type)
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
        assertEquals(unionTypes(Types.IntType, NullType), type)
    }

    @Test
    fun testCompareOperators() {
        assertEquals(
            Types.BooleanType,
            testTypeResolution("val tested = 0 < 1")
        )
        assertEquals(
            Types.BooleanType,
            testTypeResolution("val tested = 0 <= 1")
        )
        assertEquals(
            Types.BooleanType,
            testTypeResolution("val tested = 0 > 1")
        )
        assertEquals(
            Types.BooleanType,
            testTypeResolution("val tested = 0 >= 1")
        )
        assertEquals(
            Types.BooleanType,
            testTypeResolution("val tested = 0 == 1")
        )
        assertEquals(
            Types.BooleanType,
            testTypeResolution("val tested = 0 != 1")
        )
        assertEquals(
            Types.BooleanType,
            testTypeResolution("val tested = 0 === 1")
        )
        assertEquals(
            Types.BooleanType,
            testTypeResolution("val tested = 0 !== 1")
        )
    }

    @Test
    fun testCompareOperatorsMixed() {
        assertEquals(
            Types.BooleanType,
            testTypeResolution("val tested = 0f < 1")
        )
        assertEquals(
            Types.BooleanType,
            testTypeResolution("val tested = 0f <= 1")
        )
        assertEquals(
            Types.BooleanType,
            testTypeResolution("val tested = 0f > 1")
        )
        assertEquals(
            Types.BooleanType,
            testTypeResolution("val tested = 0f >= 1")
        )
        assertEquals(
            Types.BooleanType,
            testTypeResolution("val tested = 0f == 1")
        )
        assertEquals(
            Types.BooleanType,
            testTypeResolution("val tested = 0f != 1")
        )
        assertEquals(
            Types.BooleanType,
            testTypeResolution("val tested = 0f === 1")
        )
        assertEquals(
            Types.BooleanType,
            testTypeResolution("val tested = 0f !== 1")
        )
    }

    @Test
    fun testValueType() {
        val field =
            testTypeResolutionGetField(
                "value val tested = \"\""
            )
        check(field.flags.hasFlag(Flags.VALUE))
    }
}