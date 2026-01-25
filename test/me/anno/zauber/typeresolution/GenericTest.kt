package me.anno.zauber.typeresolution

import me.anno.zauber.Compile.stdlib
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.types.StandardTypes.standardClasses
import me.anno.zauber.types.Types.ArrayListType
import me.anno.zauber.types.Types.ArrayType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.LongType
import me.anno.zauber.types.Types.MapType
import me.anno.zauber.types.Types.NullableAnyType
import me.anno.zauber.types.Types.PairType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GenericTest {

    @Test
    fun testTypeWithGenerics() {
        assertEquals(
            ClassType(
                ArrayListType.clazz,
                listOf(ClassType(IntType.clazz, null))
            ),
            TypeResolutionTest.testTypeResolution("val tested: ArrayList<Int>")
        )
    }

    @Test
    fun testConstructorsWithGenerics() {
        TypeResolutionTest.defineArrayListConstructors()

        assertEquals(
            ClassType(
                ArrayListType.clazz,
                listOf(ClassType(IntType.clazz, null))
            ),
            TypeResolutionTest.testTypeResolution("val tested = ArrayList<Int>(8)")
        )
    }

    @Test
    fun testSimpleInferredGenerics() {
        val listClass = standardClasses["List"]!!
        assertEquals(
            ClassType(listClass, listOf(IntType)),
            TypeResolutionTest.testTypeResolution(
                """
                fun <V> listOf(v: V): List<V>
                val tested = listOf(0)
            """.trimIndent()
            )
        )
        assertEquals(
            ClassType(listClass, listOf(StringType)),
            TypeResolutionTest.testTypeResolution(
                """
                fun <V> listOf(v: V): List<V>
                val tested = listOf("Hello World!")
            """.trimIndent()
            )
        )
    }

    @Test
    fun testInferredMapGenerics() {
        registerMapParams()
        registerPairParams()
        registerArrayParams()

        assertEquals(
            ClassType(
                standardClasses["Map"]!!,
                listOf(StringType, IntType)
            ),
            TypeResolutionTest.testTypeResolution(
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
        TypeResolutionTest.defineArrayListConstructors()

        assertEquals(
            ClassType(
                standardClasses["List"]!!,
                listOf(ClassType(IntType.clazz, null))
            ),
            TypeResolutionTest.testTypeResolution(
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
            ClassType(
                standardClasses["List"]!!,
                listOf(ClassType(FloatType.clazz, null))
            ),
            TypeResolutionTest.testTypeResolution(
                """
                fun <V> emptyList(): List<V>
                fun <V,R> List<V>.map(map: (V) -> R): List<R>
                val tested = emptyList<Int>().map { it + 1f }
                
                // mark Int as a class (that extends Any)
                package $stdlib
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
            LongType,
            TypeResolutionTest.testTypeResolution(
                """
                fun <V> emptyList(): List<V>
                fun sum(list: List<Int>): Long
                val tested = sum(emptyList())               
            """.trimIndent()
            )
        )
    }

    private fun registerMapParams() {
        val mapClass = standardClasses["Map"]!!
        if (mapClass.typeParameters.size != 2) {
            mapClass.typeParameters = listOf(
                Parameter(0, "K", NullableAnyType, mapClass, -1),
                Parameter(1, "V", NullableAnyType, mapClass, -1),
            )
        }
    }

    private fun registerPairParams() {
        PairType.clazz
    }

    private fun registerArrayParams() {
        ArrayType.clazz
    }

    @Test
    fun testTwoStackedGenericReturnTypes() {
        val mapClass = MapType.clazz
        registerMapParams()
        registerPairParams()
        registerArrayParams()

        assertEquals(
            ClassType(
                mapClass,
                listOf(IntType, FloatType)
            ),
            TypeResolutionTest.testTypeResolution(
                """
                infix fun <F,S> F.to(s: S): Pair<F,S>
                fun <K,V> mapOf(vararg entries: Pair<K,V>): Map<K,V>
                
                val tested = mapOf(1 to 2f)   
            """.trimIndent()
            )
        )
    }

    @Test
    fun testListReduce() {
        assertEquals(
            IntType,
            TypeResolutionTest.testTypeResolution(
                """
                fun <V> emptyList(): List<V>
                fun <V> List<V>.reduce(map: (V, V) -> V): V
                val tested = emptyList<Int>().reduce { a,b -> a + b }
                
                // mark Int as a class (that extends Any)
                package $stdlib
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
        TypeResolutionTest.defineListParameters()

        assertEquals(
            ClassType(standardClasses["List"]!!, listOf(FloatType)),
            TypeResolutionTest.testTypeResolution(
                """
                fun <V> listOf(v: V): List<V>
                fun intsToFloats(v: List<Int>): List<Float>
                
                val tested = intsToFloats(listOf(1))
            """.trimIndent()
            )
        )
        assertEquals(
            ClassType(standardClasses["List"]!!, listOf(FloatType)),
            TypeResolutionTest.testTypeResolution(
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
        val listType = standardClasses["List"]!!
        val listOfInt = ClassType(listType, listOf(IntType))
        assertEquals(
            ClassType(listType, listOf(listOfInt)),
            TypeResolutionTest.testTypeResolution(
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
        val listType = standardClasses["List"]!!
        val mixedType = unionTypes(listOf(StringType, IntType, FloatType))
        assertEquals(
            ClassType(listType, listOf(mixedType)),
            TypeResolutionTest.testTypeResolution(
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
}