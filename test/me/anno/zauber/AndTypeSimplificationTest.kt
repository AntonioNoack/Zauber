package me.anno.zauber

import me.anno.zauber.IsSubTypeOfTest.Companion.andTypes
import me.anno.zauber.IsSubTypeOfTest.Companion.get
import me.anno.zauber.IsSubTypeOfTest.Companion.testInheritance
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.LongType
import me.anno.zauber.types.Types.NothingType
import me.anno.zauber.types.impl.AndType.Companion.andTypes
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.impl.UnknownType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AndTypeSimplificationTest {

    @Test
    fun testAndUnknownType() {
        val classA = IntType
        assertEquals(classA, andTypes(classA, UnknownType))
    }

    @Test
    fun testAndNothingType() {
        val classA = IntType
        assertEquals(NothingType, andTypes(classA, NothingType))
    }

    @Test
    fun testAndNullType() {
        val classA = IntType
        assertEquals(NothingType, andTypes(classA, NullType))
    }

    @Test
    fun testDisjointAndTypes() {
        val classA = IntType
        val classB = LongType
        val classC = FloatType

        assertEquals(NothingType, andTypes(classA, classB))
        assertEquals(NothingType, andTypes(classA, classB, classC))
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
        assertEquals(classC, andTypes(classA, classB, classC))
    }

}