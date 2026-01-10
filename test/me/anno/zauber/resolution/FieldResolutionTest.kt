package me.anno.zauber.resolution

import me.anno.zauber.resolution.ResolutionUtils.firstChild
import me.anno.zauber.resolution.ResolutionUtils.get
import me.anno.zauber.resolution.ResolutionUtils.getField
import me.anno.zauber.resolution.ResolutionUtils.typeResolveScope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.Types.IntType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FieldResolutionTest {

    @Test
    fun testSimple() {
        val code = """
        object Target {
            val x : Int = 0
            class Inner {
                val tested = x
            }
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val tested0 = scope["Target"]["Inner"].getField("tested").valueType!!
        assertEquals(IntType, tested0)
    }

    @Test
    fun testInnerClass() {
        val code = """
        class Target {
            val x : Int = 0
            inner class Inner {
                val tested = x
            }
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val tested0 = scope["Target"]["Inner"].getField("tested").valueType!!
        assertEquals(IntType, tested0)
    }

    @Test
    fun testWithShadowing() {
        val code = """
        object Shadowed {
            val x: Float = 0f
            object Target {
                val x : Int = 0
                class Inner {
                    val tested = x
                }
            }
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val tested0 = scope["Shadowed"]["Target"]["Inner"].getField("tested").valueType!!
        assertEquals(IntType, tested0)
    }

    @Test
    fun testWithImportMatching() {
        val code = """
        import helper002.Helper.x
        class Misleading {
            val x : Float = 0
            class Inner {
                val tested = x
            }
        }
        
        package helper002
        object Helper {
            val x : Int = 0
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val tested0 = scope["Misleading"]["Inner"].getField("tested").valueType!!
        assertEquals(IntType, tested0)
    }

    @Test
    fun testWithImportMismatch() {
        // check what this code does in Kotlin -> uses the local field
        // todo get it to prefer the local field...
        val code = """
        import helper001.Helper.x
        object Target {
            val x : Int = 0
            class Inner {
                val tested = x
            }
        }
        
        package helper001
        object Helper {
            val x : Float = 0
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val tested0 = scope["Target"]["Inner"].getField("tested").valueType!!
        assertEquals(IntType, tested0)
    }

    @Test
    fun testWithImportAndAS() {
        val code = """
        import helper001.Helper.y as x
        class Misleading {
            val x : Float = 0
            class Inner {
                val tested = x
            }
        }
        
        package helper001
        object Helper {
            val y : Int = 0
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val tested0 = scope["Misleading"]["Inner"].getField("tested").valueType!!
        assertEquals(IntType, tested0)
    }

    @Test
    fun testNested() {
        val code = """
        object Target {
            val x : Int = 0
            class MisleadingNoInstance {
                val x: Float = 0f
                class NotInnerClassC {
                    val tested = x // must be Int
                }
                object NotInnerClassO {
                    val tested = x // must be Int
                }
            }
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val misleading = scope["Target"]["MisleadingNoInstance"]
        val tested0 = misleading["NotInnerClassC"].getField("tested").valueType!!
        val tested1 = misleading["NotInnerClassO"].getField("tested").valueType!!
        assertEquals(IntType, tested0)
        assertEquals(IntType, tested1)
    }

    @Test
    fun testNestedWithInheritance1() {
        val code = """
        open class TargetBase {
            val x: Int = 0
        }
        object Target: TargetBase() {
            class MisleadingNoInstance {
                val x: Float = 0f
                class NotInnerClassC {
                    val tested = x // must be Int
                }
                object NotInnerClassO {
                    val tested = x // must be Int
                }
            }
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val misleading = scope["Target"]["MisleadingNoInstance"]
        val tested0 = misleading["NotInnerClassC"].getField("tested").valueType!!
        val tested1 = misleading["NotInnerClassO"].getField("tested").valueType!!
        assertEquals(IntType, tested0)
        assertEquals(IntType, tested1)
    }

    @Test
    fun testNestedWithInheritance2() {
        val code = """
        open class TargetBase {
            val x: Int = 0
        }
        open class MisleadingBase {
            val x: Float = 0f
        }
        object Target: TargetBase() {
            class MisleadingNoInstance: MisleadingBase() {
                class NotInnerClassC {
                    val tested = x // must be Int
                }
                object NotInnerClassO {
                    val tested = x // must be Int
                }
            }
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val misleading = scope["Target"]["MisleadingNoInstance"]
        val tested0 = misleading["NotInnerClassC"].getField("tested").valueType!!
        val tested1 = misleading["NotInnerClassO"].getField("tested").valueType!!
        assertEquals(IntType, tested0)
        assertEquals(IntType, tested1)
    }

    @Test
    fun testEnumBesideSelf() {
        val code = """
        enum class Color {
            RED
        }
        class Inner {
            val tested = Color.RED.ordinal
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope["Inner"].getField("tested").valueType!!
        assertEquals(IntType, actualType)
    }

    @Test
    fun testEnumBelowSelf() {
        val code = """
        enum class Color {
            RED;
            
            class Inner {
                val tested = RED.ordinal
            }
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope["Color"]["Inner"].getField("tested").valueType!!
        assertEquals(IntType, actualType)
    }

    @Test
    fun testEnumInsideSelf() {
        val code = """
        class Inner {
            val tested = Color.RED.ordinal
            enum class Color {
                RED;
            }
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope["Inner"].getField("tested").valueType!!
        assertEquals(IntType, actualType)
    }

    @Test
    fun testEnumBesideSelf2() {
        val code = """
        enum class Color {
            RED
        }
        class Inner {
            val tested = Color.RED
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope["Inner"].getField("tested").valueType!!
        val colorType = scope["Color"].typeWithArgs
        assertEquals(colorType, actualType)
    }

    @Test
    fun testEnumBelowSelf2() {
        val code = """
        enum class Color {
            RED;
            
            class Inner {
                val tested = RED
            }
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope["Color"]["Inner"].getField("tested").valueType!!
        val expectedType = scope["Color"].typeWithArgs
        assertEquals(expectedType, actualType)
    }

    @Test
    fun testEnumInsideSelf2() {
        val code = """
        class Inner {
            val tested = Color.RED
            enum class Color {
                RED;
            }
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope["Inner"].getField("tested").valueType!!
        val colorType = scope["Inner"]["Color"].typeWithoutArgs
        assertEquals(colorType, actualType)
    }

    @Test
    fun testExplicitCompanionForOther() {
        val code = """
        class Wrapper {
            companion object {
                val x = 0
            }
        }
        
        class Inner {
            val tested = Wrapper.Companion.x
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope["Inner"].getField("tested").valueType!!
        assertEquals(IntType, actualType)
    }

    @Test
    fun testObjectInClass() {
        val code = """
        class Wrapper {
            object Inside {
                val x = 0
            }
        }
        
        class Inner {
            val tested = Wrapper.Inside.x
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope["Inner"].getField("tested").valueType!!
        assertEquals(IntType, actualType)
    }

    @Test
    fun testCompanionBeingOptionalForOther() {
        val code = """
        class Wrapper {
            companion object {
                val x = 0
            }
        }
        
        class Inner {
            val tested = Wrapper.x
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope["Inner"].getField("tested").valueType!!
        assertEquals(IntType, actualType)
    }

    @Test
    fun testCompanionBeingOptionalInsideSelf() {
        val code = """
        class Inner {
            val tested = x
            companion object {
                val x = 0
            }
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope["Inner"].getField("tested").valueType!!
        assertEquals(IntType, actualType)
    }

    @Test
    fun testCompanionBeingOptionalForImport() {
        val code = """
        import helper004f.Wrapper
        class Inner {
            val tested = Wrapper.x
        }
        
        package helper004f
        class Wrapper {
            companion object {
                val x = 0
            }
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope["Inner"].getField("tested").valueType!!
        assertEquals(IntType, actualType)
    }

    @Test
    fun testCompanionBeingOptionalForImported() {
        val code = """
        import helper005.Wrapper.x
        class Inner {
            val tested = x
        }
        
        package helper005
        class Wrapper {
            companion object {
                val x = 0
            }
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope["Inner"].getField("tested").valueType!!
        assertEquals(IntType, actualType)
    }

    @Test
    fun testFieldExtension() {
        val code = """
        val Int.next get() = 0f
        class Inner {
            fun <V: Int> V.test(): Int {
                val tested = next
            }
        }
        package zauber
        class Int
        class Float
        """.trimIndent()
        val scope = typeResolveScope(code)
        val methodScope = scope["Inner"].firstChild(ScopeType.METHOD)
        // why is this called split? -> because there is a field declaration
        val splitScope = methodScope.firstChild(ScopeType.METHOD_BODY)
        val actualType = splitScope.getField("tested").valueType!!
        assertEquals(FloatType, actualType)
    }

    @Test
    fun testFieldExtensionInner() {
        val code = """
        val Int.next get() = 0f
        class Inner {
            fun <V: Int> V.test(): Int {
                class Tested {
                    val tested = next /* called on V */
                }
            }
        }
        package zauber
        class Int
        class Float
        """.trimIndent()
        val scope = typeResolveScope(code)
        val method = scope["Inner"].firstChild(ScopeType.METHOD)
        val actualType = method["Tested"].getField("tested").valueType!!
        assertEquals(FloatType, actualType)
    }

    @Test
    fun testFieldExtensionImported() {
        val code = """
        import helper003.next
        class Inner {
            fun <V: Int> V.test(): Int {
                val tested = next
            }
        }
        
        package helper003
        val Int.next get() = 0f
        
        package zauber
        class Int
        class Float
        """.trimIndent()
        val scope = typeResolveScope(code)
        val methodScope = scope["Inner"].firstChild(ScopeType.METHOD)
        // why is this called split? -> because there is a field declaration
        val splitScope = methodScope.firstChild(ScopeType.METHOD_BODY)
        val actualType = splitScope.getField("tested").valueType!!
        assertEquals(FloatType, actualType)
    }
}