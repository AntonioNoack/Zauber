package me.anno.zauber.resolution

import me.anno.zauber.resolution.ResolutionUtils.getField
import me.anno.zauber.resolution.ResolutionUtils.typeResolveScope
import me.anno.zauber.types.Types.IntType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConstructorResolutionTest {
    @Test
    fun testSimple() {
        val code = """
        class X(val x: Int)
        val tested = X(0).x
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope.getField("tested").valueType!!
        assertEquals(IntType, actualType)
    }

    @Test
    fun testWithShadowing() {
    }

    @Test
    fun testWithImport() {
        val code = """
        import helper004m.X
        val tested = X(0).x
        
        package helper004c
        class X(val x: Int)
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope.getField("tested").valueType!!
        assertEquals(IntType, actualType)
    }

    @Test
    fun testWithImportAndAS() {
        val code = """
        import helper004m.X as Z
        val tested = Z(0).x
        
        package helper004c
        class X(val x: Int)
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope.getField("tested").valueType!!
        assertEquals(IntType, actualType)
    }

    @Test
    fun testWithTypeAlias() {
        val code = """
        class X(val x: Int)
        typealias Y = X
        val tested = Y(0).x
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope.getField("tested").valueType!!
        assertEquals(IntType, actualType)
    }

    @Test
    fun testWithImportedTypeAliasAndAS() {
        val code = """
        import helper004m.Y as Z
        val tested = Z(0).x
        
        package helper004c
        class X(val x: Int)
        typealias Y = X
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope.getField("tested").valueType!!
        assertEquals(IntType, actualType)
    }
}