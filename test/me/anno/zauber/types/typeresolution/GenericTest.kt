package me.anno.zauber.types.typeresolution

import me.anno.zauber.Compile.STDLIB_NAME
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes
import me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GenericTest {

    @Test
    fun testTypeWithGenerics() {
        val actual = testTypeResolution("val tested: ArrayList<Int>")
        val expected = Types.ArrayListType.withTypeParameter(Types.IntType)
        assertEquals(expected, actual)
    }

    @Test
    fun testConstructorsWithGenerics() {
        val actual = testTypeResolution(
            """
                val tested = ArrayList<Int>(8)
                
                package zauber
                class ArrayList<Int>(initialCapacity: Int)
            """.trimIndent()
        )
        val expected = Types.ArrayListType.withTypeParameter(Types.IntType)
        assertEquals(expected, actual)
    }

    @Test
    fun testSimpleInferredGenerics() {
        assertEquals(
            Types.ListType.withTypeParameter(Types.IntType),
            testTypeResolution(
                """
                fun <V> listOf(v: V): List<V>
                val tested = listOf(0)
            """.trimIndent()
            )
        )
        assertEquals(
            Types.ListType.withTypeParameter(Types.StringType),
            testTypeResolution(
                """
                fun <V> listOf(v: V): List<V>
                val tested = listOf("Hello World!")
            """.trimIndent()
            )
        )
    }

    @Test
    fun testInferredMapGenerics() {
        assertEquals(
            Types.MapType.withTypeParameters(listOf(Types.StringType, Types.IntType)),
            testTypeResolution(
                """
                fun <K, V> mapOf(entry: Pair<K,V>): Map<K,V>
                infix fun <F,S> F.to(other: S): Pair<F,S>
                val tested = mapOf("" to 0)
            """.trimIndent()
            )
        )
    }

    @Test
    fun testGenericFunction() {
        assertEquals(
            Types.ListType.withTypeParameter(Types.IntType),
            testTypeResolution(
                """
                fun <V> emptyList(): List<V> = ArrayList<V>(0)
                val tested = emptyList<Int>()
            """.trimIndent()
            )
        )
    }

    @Test
    fun testGenericsMap() {
        assertEquals(
            Types.ListType.withTypeParameter(Types.FloatType),
            testTypeResolution(
                """
                fun <V> emptyList(): List<V>
                fun <V,R> List<V>.map(map: (V) -> R): List<R>
                val tested = emptyList<Int>().map { it + 1f }
                
                // mark Int as a class (that extends Any)
                package $STDLIB_NAME
                class Int: Any() {
                    operator fun plus(other: Int): Int
                    operator fun plus(other: Float): Float
                }
                // mark Any as a class
                class Any()
            """.trimIndent()
            )
        )
    }

    @Test
    fun testEmptyListAsParameter() {
        assertEquals(
            Types.LongType,
            testTypeResolution(
                """
                fun <V> emptyList(): List<V>
                fun sum(list: List<Int>): Long
                val tested = sum(emptyList())               
            """.trimIndent()
            )
        )
    }

    @Test
    fun testTwoStackedGenericReturnTypes() {
        val type = testTypeResolution(
            """
                infix fun <F,S> F.to(s: S): Pair<F,S>
                fun <K,V> mapOf(vararg entries: Pair<K,V>): Map<K,V>
                
                val tested = mapOf(1 to 2f)   
            """.trimIndent()
        )
        assertEquals(Types.MapType.withTypeParameters(listOf(Types.IntType, Types.FloatType)), type)
    }

    @Test
    fun testListReduceWithLambda() {
        // todo this passes alone, but fails in a group...
        val actualType = testTypeResolution(
            """
                fun <V> emptyList(): List<V>
                fun <V> List<V>.reduce(map: (V, V) -> V): V
                val tested = emptyList<Int>().reduce { a,b -> a + b }
                
                // mark Int as a class (that extends Any)
                package $STDLIB_NAME
                class Int: Any() {
                    operator fun plus(other: Int): Int
                    operator fun plus(other: Float): Float
                }
                // mark Any as a class
                class Any()
                // give some List-details
                interface List<V> {
                    val size: Int
                    operator fun get(index: Int): V
                }
            """.trimIndent()
        )
        assertEquals(Types.IntType, actualType)
    }

    @Test
    fun testListReduceWithTypeMethod() {
        assertEquals(
            Types.IntType,
            testTypeResolution(
                """
                fun <V> emptyList(): List<V>
                fun <V> List<V>.reduce(map: (V, V) -> V): V
                val tested = emptyList<Int>().reduce(Int::plus)
                
                // mark Int as a class (that extends Any)
                package $STDLIB_NAME
                class Int: Any() {
                    operator fun plus(other: Int): Int
                    operator fun plus(other: Float): Float
                }
                // mark Any as a class
                class Any()
                // give some List-details
                interface List<V> {
                    val size: Int
                    operator fun get(index: Int): V
                }
            """.trimIndent()
            )
        )
    }

    @Test
    fun testListsAreNotConfused() {
        assertEquals(
            Types.ListType.withTypeParameter(Types.FloatType),
            testTypeResolution(
                """
                fun <V> listOf(v: V): List<V>
                fun intsToFloats(v: List<Int>): List<Float>
                
                val tested = intsToFloats(listOf(1))
            """.trimIndent()
            )
        )
        assertEquals(
            Types.ListType.withTypeParameter(Types.FloatType),
            testTypeResolution(
                """
                fun listOf(v: Int): List<Int>
                fun intsToFloats(v: List<Int>): List<Float>
                
                val tested = intsToFloats(listOf(1))
            """.trimIndent()
            )
        )
    }

    @Test
    fun testLambdaInsideLambda() {
        // what about listOf("1,2,3").map{it.split(',').map{it.toInt()}}?
        //  can we somehow hide lambdas? I don't think so...
        val listOfInt = Types.ListType.withTypeParameters(listOf(Types.IntType))
        assertEquals(
            Types.ListType.withTypeParameter(listOfInt),
            testTypeResolution(
                """
                fun <V> listOf(v: V): List<V>
                fun <V,R> List<V>.map(map: (V) -> R): List<R>
                fun String.split(separator: Char): List<String>
                fun String.toInt(): Int
                
                val tested = listOf("1,2,3").map{it.split(',').map{it.toInt()}}
                
                // define types as classes
                package zauber
                interface List<V>
                class Int
                class String
                class Char
            """.trimIndent()
            )
        )
    }

    @Test
    fun testMixedList() {
        val mixedType = unionTypes(listOf(Types.StringType, Types.IntType, Types.FloatType))
        assertEquals(
            Types.ListType.withTypeParameter(mixedType),
            testTypeResolution(
                """
                fun <V> listOf(vararg values: V): List<V>
                
                val tested = listOf("1", 1, 1f)
                
                // define types as classes
                package zauber
                interface List<V>
                class Int
                class String
                class Float
            """.trimIndent()
            )
        )
    }

    @Test
    fun testGenericField() {
        assertEquals(
            Types.StringType,
            testTypeResolution(
                """
                class A<V>(val a: V)
                
                val tested = A("").a
            """.trimIndent()
            )
        )
    }

    @Test
    fun testGenericFieldInSuperClass() {
        assertEquals(
            Types.StringType,
            testTypeResolution(
                """
                class A<V>(val a: V)
                class B<W>(a: W): A<W>(a)
                
                val tested = B("").a
            """.trimIndent()
            )
        )
    }
}