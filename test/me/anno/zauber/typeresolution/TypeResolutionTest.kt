package me.anno.zauber.typeresolution

import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.ASTClassScanner.Companion.collectNamedClasses
import me.anno.zauber.ast.rich.Keywords.hasFlag
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.expansion.DefaultParameterExpansion.createDefaultParameterFunctions
import me.anno.zauber.expansion.OverriddenMethods.resolveOverrides
import me.anno.zauber.tokenizer.ZauberTokenizer
import me.anno.zauber.typeresolution.TypeResolution.resolveTypesAndNames
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.StandardTypes.standardClasses
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.ArrayListType
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

        fun testTypeResolutionGetField(code: String): Field {
            val testScopeName = "test${ctr++}"
            val tokens = ZauberTokenizer(
                """
            package $testScopeName
            
            $code
        """.trimIndent(), "?"
            ).tokenize()
            collectNamedClasses(tokens)
            ZauberASTBuilder(tokens, root).readFileLevel()
            createDefaultParameterFunctions(root)
            val testScope = root.children.first { it.name == testScopeName }
            resolveOverrides(testScope)
            resolveTypesAndNames(testScope)
            return testScope.fields.first { it.name == "tested" }
        }

        fun testTypeResolution(code: String): Type {
            val field = testTypeResolutionGetField(code)
            return field.valueType
                ?: throw IllegalStateException("Could not resolve type for $field")
        }

        fun testMethodBodyResolution(code: String): List<Type> {
            val testScopeName = "test${ctr++}"
            val tokens = ZauberTokenizer(
                """
            package $testScopeName
            
            $code
        """.trimIndent(), "?"
            ).tokenize()
            ZauberASTBuilder(tokens, root).readFileLevel()
            createDefaultParameterFunctions(root)
            val testScope = root.children.first { it.name == testScopeName }
            resolveTypesAndNames(testScope)
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
                        true, null
                    )
                    val type = TypeResolution.resolveType(context, expr)
                    types.add(type)
                }
            }
            scan(method.body!!)
            return types
        }

        fun defineArrayListConstructors() {
            val arrayListType = ArrayListType.clazz

            // we need to define the constructor without any args
            val constructors = arrayListType.constructors
            if (constructors.none { it.valueParameters.isEmpty() }) {
                val scope = arrayListType.getOrCreatePrimConstructorScope()
                scope.selfAsConstructor = Constructor(
                    emptyList(),
                    scope, null, null,
                    Keywords.NONE, -1
                )
            }
            if (constructors.none { it.valueParameters.size == 1 }) {
                val scope = arrayListType.getOrPut(arrayListType.generateName("constructor"), ScopeType.CONSTRUCTOR)
                scope.selfAsConstructor = Constructor(
                    listOf(Parameter("size", IntType, arrayListType, -1)),
                    scope, null, null,
                    Keywords.NONE, -1
                )
            }
        }

        fun defineListParameters() {
            ListType.clazz
        }
    }

    @Test
    fun testConstants() {
        assertEquals(BooleanType, testTypeResolution("val tested = true"))
        assertEquals(BooleanType, testTypeResolution("val tested = false"))
        assertEquals(NullType, testTypeResolution("val tested = null"))
        assertEquals(IntType, testTypeResolution("val tested = 0"))
        assertEquals(LongType, testTypeResolution("val tested = 0L"))
        assertEquals(FloatType, testTypeResolution("val tested = 0f"))
        assertEquals(FloatType, testTypeResolution("val tested = 0.0f"))
        assertEquals(DoubleType, testTypeResolution("val tested = 0d"))
        assertEquals(DoubleType, testTypeResolution("val tested = 0.0"))
        assertEquals(DoubleType, testTypeResolution("val tested = 1e3"))
        assertEquals(CharType, testTypeResolution("val tested = ' '"))
        assertEquals(StringType, testTypeResolution("val tested = \"Test 123\""))
    }

    @Test
    fun testNullableTypes() {
        assertEquals(
            UnionType(listOf(ClassType(BooleanType.clazz, null), NullType)),
            testTypeResolution("val tested: Boolean?")
        )
    }

    @Test
    fun testConstructorWithParameter() {
        val intArrayType = standardClasses["IntArray"]!!
        // we need to define the constructor without any args
        val constructors = intArrayType.constructors
        if (constructors.none { it.valueParameters.size == 1 }) {
            val scope = intArrayType.getOrCreatePrimConstructorScope()
            scope.selfAsConstructor = Constructor(
                listOf(Parameter("size", IntType, intArrayType, -1)),
                scope, null, null,
                Keywords.NONE, -1
            )
        }
        assertEquals(intArrayType.typeWithoutArgs, testTypeResolution("val tested = IntArray(5)"))
    }

    @Test
    fun testGetOperator() {
        val type = testTypeResolution(
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
        val type = testTypeResolution(
            """
            val x: Int?
            val tested = x ?: 0f
        """.trimIndent()
        )
        assertEquals(unionTypes(IntType, FloatType), type)
    }

    @Test
    fun testNullableGetOperator() {
        val type = testTypeResolution(
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
        assertEquals(BooleanType, testTypeResolution("val tested = 0 < 1"))
        assertEquals(BooleanType, testTypeResolution("val tested = 0 <= 1"))
        assertEquals(BooleanType, testTypeResolution("val tested = 0 > 1"))
        assertEquals(BooleanType, testTypeResolution("val tested = 0 >= 1"))
        assertEquals(BooleanType, testTypeResolution("val tested = 0 == 1"))
        assertEquals(BooleanType, testTypeResolution("val tested = 0 != 1"))
        assertEquals(BooleanType, testTypeResolution("val tested = 0 === 1"))
        assertEquals(BooleanType, testTypeResolution("val tested = 0 !== 1"))
    }

    @Test
    fun testCompareOperatorsMixed() {
        assertEquals(BooleanType, testTypeResolution("val tested = 0f < 1"))
        assertEquals(BooleanType, testTypeResolution("val tested = 0f <= 1"))
        assertEquals(BooleanType, testTypeResolution("val tested = 0f > 1"))
        assertEquals(BooleanType, testTypeResolution("val tested = 0f >= 1"))
        assertEquals(BooleanType, testTypeResolution("val tested = 0f == 1"))
        assertEquals(BooleanType, testTypeResolution("val tested = 0f != 1"))
        assertEquals(BooleanType, testTypeResolution("val tested = 0f === 1"))
        assertEquals(BooleanType, testTypeResolution("val tested = 0f !== 1"))
    }

    @Test
    fun testValueType() {
        val field = testTypeResolutionGetField("value val tested = \"\"")
        check(field.keywords.hasFlag(Keywords.VALUE))
    }
}