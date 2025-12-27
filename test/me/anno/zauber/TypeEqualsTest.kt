package me.anno.zauber

import me.anno.zauber.Compile.root
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.impl.UnionType
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
        fun gen() = UnionType(listOf(BooleanType, NullType))
        assertEquals(gen(), gen())
    }
}