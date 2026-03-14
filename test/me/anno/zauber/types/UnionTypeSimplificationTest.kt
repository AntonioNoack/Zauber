package me.anno.zauber.types

import me.anno.zauber.types.IsSubTypeOfTest.Companion.get
import me.anno.zauber.types.IsSubTypeOfTest.Companion.testInheritance
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.NothingType
import me.anno.zauber.types.impl.UnionType
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes
import me.anno.zauber.types.impl.UnknownType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UnionTypeSimplificationTest {

    @Test
    fun testUnionSelf() {
        val classA = IntType
        assertEquals(classA, unionTypes(classA, classA))
    }

    @Test
    fun testUnionNothing() {
        val classA = IntType
        assertEquals(classA, unionTypes(classA, NothingType))
    }

    @Test
    fun testUnionUnknown() {
        val classA = IntType
        assertEquals(UnknownType, unionTypes(classA, UnknownType))
    }

    @Test
    fun testDisjointUnionTypes() {
        val scope = """
    class A
    class B
    class C
""".testInheritance()
        val classA = scope["A"]
        val classB = scope["B"]
        val classC = scope["C"]

        assertEquals(UnionType(listOf(classA, classB)), unionTypes(classA, classB))
        assertEquals(UnionType(listOf(classA, classB, classC)),
            IsSubTypeOfTest.unionTypes(classA, classB, classC)
        )
    }

    @Test
    fun testHierarchicalUnionTypes() {
        val scope = """
            class A
            class B: A()
            class C: B()
        """.testInheritance()
        val classA = scope["A"]
        val classB = scope["B"]
        val classC = scope["C"]

        assertEquals(classA, unionTypes(classA, classB))
        assertEquals(classA, unionTypes(classA, classC))
        assertEquals(classB, unionTypes(classB, classC))
        assertEquals(classA, IsSubTypeOfTest.unionTypes(classA, classB, classC))
    }

}