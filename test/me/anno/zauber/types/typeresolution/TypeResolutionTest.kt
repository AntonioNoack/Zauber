package me.anno.zauber.types.typeresolution

import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.Keywords.hasFlag
import me.anno.zauber.ast.rich.ZauberASTClassScanner.Companion.collectNamedClasses
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.expansion.DefaultParameterExpansion.createDefaultParameterFunctions
import me.anno.zauber.expansion.OverriddenMethods.resolveOverrides
import me.anno.zauber.tokenizer.ZauberTokenizer
import me.anno.zauber.typeresolution.TypeResolution.resolveTypesAndNames
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.StandardTypes.standardClasses
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.Types.ArrayListType
import me.anno.zauber.types.Types.ArrayType
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.CharType
import me.anno.zauber.types.Types.DoubleType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.ListType
import me.anno.zauber.types.Types.LongType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.impl.UnionType
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypeResolutionTest {

    companion object {
        var ctr = 0

        fun testTypeResolution0(code: String): Scope {

            // clean slate
            root.clear()
            Types.register()

            val testScopeName = "test${ctr++}"
            val tokens = ZauberTokenizer(
                """
            package $testScopeName
            
            $code
        """.trimIndent(), "Test.zbr"
            ).tokenize()
            collectNamedClasses(tokens)
            ZauberASTBuilder(tokens, root).readFileLevel()
            createDefaultParameterFunctions(root)
            val testScope = root.children.first { it.name == testScopeName }
            resolveOverrides(testScope)
            resolveTypesAndNames(testScope)
            return testScope
        }

        fun testTypeResolutionGetField(code: String): Field {
            return testTypeResolution0(code).fields.first { it.name == "tested" }
        }

        fun testTypeResolution(code: String): Type {
            val field = testTypeResolutionGetField(code)
            return field.valueType
                ?: throw IllegalStateException("Could not resolve type for $field")
        }

        fun testMethodBodyResolution(code: String): List<Type> {
            val testScope = testTypeResolution0(code)
            val method = testScope.methods.first { it.name == "tested" }
            val types = ArrayList<Type>()
            fun scan(expr: Expression) {
                if (expr is ExpressionList) {
                    for (exprI in expr.list) {
                        scan(exprI)
                    }
                } else {
                    val context = _root_ide_package_.me.anno.zauber.typeresolution.ResolutionContext(
                        method.selfType,
                        true,
                        null,
                        emptyMap()
                    )
                    val type = _root_ide_package_.me.anno.zauber.typeresolution.TypeResolution.resolveType(context, expr)
                    types.add(type)
                }
            }
            scan(method.body!!)
            return types
        }

        fun defineListParameters() {
            ListType.clazz
        }
    }

    @Test
    fun testConstants() {
        assertEquals(BooleanType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = true")
        )
        assertEquals(BooleanType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = false")
        )
        assertEquals(NullType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = null")
        )
        assertEquals(IntType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 0")
        )
        assertEquals(LongType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 0L")
        )
        assertEquals(FloatType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 0f")
        )
        assertEquals(FloatType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 0.0f")
        )
        assertEquals(DoubleType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 0d")
        )
        assertEquals(DoubleType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 0.0")
        )
        assertEquals(DoubleType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 1e3")
        )
        assertEquals(CharType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = ' '")
        )
        assertEquals(StringType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = \"Test 123\"")
        )
    }

    @Test
    fun testNullableTypes() {
        assertEquals(
            UnionType(listOf(ClassType(BooleanType.clazz, null), NullType)),
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested: Boolean?")
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
        assertEquals(ArrayType.withTypeParameter(IntType),
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution(code)
        )
    }
    
    @Test
    fun testGetOperator() {
        val type =
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution(
                """
            class Node(val value: Int)
            val x: Node
            val tested = x.value
        """.trimIndent()
            )
        assertEquals(IntType, type)
    }

    @Test
    fun testIfNullOperator() {
        val type =
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution(
                """
            val x: Int?
            val tested = x ?: 0f
        """.trimIndent()
            )
        assertEquals(unionTypes(IntType, FloatType), type)
    }

    @Test
    fun testNullableGetOperator() {
        val type =
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution(
                """
            class Node(val parent: Node?, val value: Int)
            val x: Node?
            val tested = x?.parent?.value
        """.trimIndent()
            )
        assertEquals(unionTypes(IntType, NullType), type)
    }

    @Test
    fun testCompareOperators() {
        assertEquals(BooleanType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 0 < 1")
        )
        assertEquals(BooleanType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 0 <= 1")
        )
        assertEquals(BooleanType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 0 > 1")
        )
        assertEquals(BooleanType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 0 >= 1")
        )
        assertEquals(BooleanType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 0 == 1")
        )
        assertEquals(BooleanType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 0 != 1")
        )
        assertEquals(BooleanType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 0 === 1")
        )
        assertEquals(BooleanType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 0 !== 1")
        )
    }

    @Test
    fun testCompareOperatorsMixed() {
        assertEquals(BooleanType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 0f < 1")
        )
        assertEquals(BooleanType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 0f <= 1")
        )
        assertEquals(BooleanType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 0f > 1")
        )
        assertEquals(BooleanType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 0f >= 1")
        )
        assertEquals(BooleanType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 0f == 1")
        )
        assertEquals(BooleanType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 0f != 1")
        )
        assertEquals(BooleanType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 0f === 1")
        )
        assertEquals(BooleanType,
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution("val tested = 0f !== 1")
        )
    }

    @Test
    fun testValueType() {
        val field =
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolutionGetField(
                "value val tested = \"\""
            )
        check(field.keywords.hasFlag(Keywords.VALUE))
    }
}