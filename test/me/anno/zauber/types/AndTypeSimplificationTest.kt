package me.anno.zauber.types

import me.anno.zauber.types.IsSubTypeOfTest.Companion.get
import me.anno.zauber.types.IsSubTypeOfTest.Companion.testInheritance
import me.anno.zauber.types.impl.arithmetic.AndType.Companion.andTypes
import me.anno.zauber.types.impl.arithmetic.NullType
import me.anno.zauber.types.impl.arithmetic.UnknownType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AndTypeSimplificationTest {

    @Test
    fun testAndUnknownType() {
        val classA = Types.Int
        assertEquals(classA, andTypes(classA, UnknownType))
    }

    @Test
    fun testAndNothingType() {
        val classA = Types.Int
        assertEquals(Types.Nothing, andTypes(classA, Types.Nothing))
    }

    @Test
    fun testAndNullType() {
        val classA = Types.Int
        assertEquals(Types.Nothing, andTypes(classA, NullType))
    }

    @Test
    fun testDisjointAndTypes() {
        val classA = Types.Int
        val classB = Types.Long
        val classC = Types.Float

        assertEquals(Types.Nothing, andTypes(classA, classB))
        assertEquals(Types.Nothing, IsSubTypeOfTest.Companion.andTypes(classA, classB, classC))
    }

    @Test
    fun testHierarchicalAndTypes() {
        val scope = """
            open class A
            open class B: A()
            class C: B()
        """.testInheritance()
        val classA = scope["A"]
        val classB = scope["B"]
        val classC = scope["C"]

        assertEquals(classB, andTypes(classA, classB))
        assertEquals(classC, andTypes(classA, classC))
        assertEquals(classC, andTypes(classB, classC))
        assertEquals(classC, IsSubTypeOfTest.Companion.andTypes(classA, classB, classC))
    }

}