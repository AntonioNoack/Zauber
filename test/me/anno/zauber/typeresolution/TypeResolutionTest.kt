package me.anno.zauber.typeresolution

import me.anno.zauber.Compile.root
import me.anno.zauber.astbuilder.ASTBuilder
import me.anno.zauber.astbuilder.Constructor
import me.anno.zauber.astbuilder.Parameter
import me.anno.zauber.expansion.DefaultParameterExpansion.createDefaultParameterFunctions
import me.anno.zauber.tokenizer.Tokenizer
import me.anno.zauber.typeresolution.TypeResolution.resolveTypesAndNames
import me.anno.zauber.types.StandardTypes.standardClasses
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.CharType
import me.anno.zauber.types.Types.DoubleType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.LongType
import me.anno.zauber.types.Types.NullableAnyType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.impl.UnionType
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

// todo test all type-resolution scenarios
// todo test the special "Self" type

class TypeResolutionTest {

    companion object {
        var ctr = 0

        fun testTypeResolution(code: String): Type {
            val testScopeName = "test${ctr++}"
            val tokens = Tokenizer(
                """
            package $testScopeName
            
            $code
        """.trimIndent(), "?"
            ).tokenize()
            ASTBuilder(tokens, root).readFileLevel()
            createDefaultParameterFunctions(root)
            val testScope = root.children.first { it.name == testScopeName }
            resolveTypesAndNames(testScope)
            val field = testScope.fields.first { it.name == "tested" }
            return field.valueType
                ?: throw IllegalStateException("Could not resolve type for $field")
        }

        fun defineArrayListConstructors() {
            val arrayListType = standardClasses["ArrayList"]!!
            if (arrayListType.typeParameters.isEmpty()) {
                arrayListType.typeParameters += Parameter(
                    false, false, false,
                    "X", NullableAnyType, null, arrayListType, -1
                )
            }

            // we need to define the constructor without any args
            val constructors = arrayListType.constructors
            if (constructors.none { it.valueParameters.isEmpty() }) {
                constructors.add(
                    Constructor(
                        arrayListType.typeWithoutArgs, emptyList(),
                        arrayListType.getOrCreatePrimConstructorScope(), null, null,
                        emptyList(), -1
                    )
                )
            }
            if (constructors.none { it.valueParameters.size == 1 }) {
                constructors.add(
                    Constructor(
                        arrayListType.typeWithoutArgs, listOf(
                            Parameter(
                                false, false, false, "size",
                                IntType, null, arrayListType, -1
                            ),
                        ),
                        arrayListType.getOrCreatePrimConstructorScope(), null, null,
                        emptyList(), -1
                    )
                )
            }
        }

        fun defineListParameters() {
            val arrayListType = standardClasses["List"]!!
            if (arrayListType.typeParameters.isEmpty()) {
                arrayListType.typeParameters += Parameter(
                    false, false, false,
                    "X", NullableAnyType, null, arrayListType, -1
                )
            }
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
            constructors.add(
                Constructor(
                    intArrayType.typeWithoutArgs, listOf(
                        Parameter(
                            false, false, false,
                            "size", IntType, null, intArrayType, -1
                        )
                    ),
                    intArrayType.getOrCreatePrimConstructorScope(), null, null,
                    emptyList(), -1
                )
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
    fun testSelfType() {
        // this has a hacky solution, how does it know of the type being B???
        //  -> it was using the constructor parameter...
        val type0 = testTypeResolution(
            """
            open class A(val other: Self?)
            class B(other: Self?): A(other)
            val tested = B(null).other
        """.trimIndent()
        )
        check(type0 is UnionType && NullType in type0.types && type0.types.size == 2)
        val type1 = type0.types.first { it != NullType }
        println("Resolved Self to $type1 (should be B)")
        assertTrue(type1 is ClassType)
        assertTrue((type1 as ClassType).clazz.name == "B")
        println("Fields[$type1]: ${type1.clazz.fields}")
        assertFalse(type1.clazz.fields.any { it.name == "other" })
    }

    @Test
    fun testSelfType2() {
        // todo somehow the field is missing??? How???
        val type = testTypeResolution(
            """
            open class A(val other: Self?)
            class B(other: Self?): A(other)
            val tested = B(null).other!!
        """.trimIndent()
        )
        println("Resolved Self to $type (should be B)")
        assertTrue(type is ClassType)
        assertTrue((type as ClassType).clazz.name == "B")
    }

}