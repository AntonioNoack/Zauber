package me.anno.zauber.types

import me.anno.zauber.Compile.root
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.arithmetic.NullType
import me.anno.zauber.types.impl.arithmetic.UnionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypeEqualsTest {
    @Test
    fun testClassType() {
        fun gen() = ClassType(root, null)
        assertEquals(gen(), gen())
    }

    @Test
    fun testUnionType() {
        fun gen() = UnionType(listOf(Types.Boolean, NullType))
        assertEquals(gen(), gen())
    }
}