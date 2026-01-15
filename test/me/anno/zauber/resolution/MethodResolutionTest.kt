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
import org.junit.jupiter.api.assertThrows

class MethodResolutionTest {

    @Test
    fun testSimple() {
        val code = """
        object Target {
            fun x(): Int = 0
            class Inner {
                val tested = x()
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
            fun x(): Int = 0
            inner class Inner {
                val tested = x()
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
            fun x(): Float = 0f
            object Target {
                fun x(): Int = 0
                class Inner {
                    val tested = x()
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
        import helper001.Helper.x
        class Misleading {
            fun x(): Float = 0
            class Inner {
                val tested = x()
            }
        }
        
        package helper001
        object Helper {
            fun x(): Int = 0
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val tested0 = scope["Misleading"]["Inner"].getField("tested").valueType!!
        assertEquals(IntType, tested0)
    }

    @Test
    fun testWithImportFromPackageMatching() {
        val code = """
        import helper001.x
        class Misleading {
            fun x(): Float = 0
            class Inner {
                val tested = x()
            }
        }
        
        package helper001
        fun x(): Int = 0
        """.trimIndent()
        val scope = typeResolveScope(code)
        val tested0 = scope["Misleading"]["Inner"].getField("tested").valueType!!
        assertEquals(IntType, tested0)
    }

    @Test
    fun testWithImportMismatch() {
        val code = """
        import helper001.Helper.x
        object Target {
            fun x(): Int = 0
            class Inner {
                val tested = x()
            }
        }
        
        package helper001
        object Helper {
            fun x(): Float = 0
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
            fun x(): Float = 0
            class Inner {
                val tested = x()
            }
        }
        
        package helper001
        object Helper {
            fun y() : Int = 0
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
            fun x(): Int = 0
            class MisleadingNoInstance {
                fun x(): Float = 0f
                class NotInnerClassC {
                    val tested = x() // must be Int
                }
                object NotInnerClassO {
                    val tested = x() // must be Int
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
            fun x(): Int = 0
        }
        object Target: TargetBase() {
            class MisleadingNoInstance {
                fun x(): Float = 0f
                class NotInnerClassC {
                    val tested = x() // must be Int
                }
                object NotInnerClassO {
                    val tested = x() // must be Int
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
            fun x(): Int = 0
        }
        open class MisleadingBase {
            fun x(): Float = 0f
        }
        object Target: TargetBase() {
            class MisleadingNoInstance: MisleadingBase() {
                class NotInnerClassC {
                    val tested = x() // must be Int
                }
                object NotInnerClassO {
                    val tested = x() // must be Int
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
            RED;
            
            fun x(): Int = 0
        }
        class Inner {
            val tested = Color.RED.x()
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
            
            fun x(): Int = 0
            
            class Inner {
                val tested = RED.x()
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
            val tested = Color.RED.x()
            enum class Color {
                RED;
            
                fun x(): Int = 0
            }
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope["Inner"].getField("tested").valueType!!
        assertEquals(IntType, actualType)
    }

    @Test
    fun testMethodExtension() {
        val code = """
        fun Int.next() = 0f
        class Inner {
            fun <V: Int> V.test(): Int {
                val tested = next()
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
    fun testMethodExtensionInner() {
        // todo why is this failing parsing???
        val code = """
        fun Int.next() = 0f
        class Inner {
            fun <V: Int> V.test(): Int {
                class Tested {
                    val tested = next()
                }
            }
        }
        package zauber
        class Int
        class Float
        """.trimIndent()
        val scope = typeResolveScope(code)
        val methodScope = scope["Inner"].firstChild(ScopeType.METHOD)
        val actualType = methodScope["Tested"].getField("tested").valueType!!
        assertEquals(FloatType, actualType)
    }

    @Test
    fun testCompanionBeingOptionalForOther() {
        val code = """
        class Wrapper {
            companion object {
                fun x() = 0
            }
        }
        
        class Inner {
            val tested = Wrapper.x()
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
            val tested = x()
            companion object {
                fun x() = 0
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
        import helper004m.Wrapper
        class Inner {
            val tested = Wrapper.x()
        }
        
        package helper004m
        class Wrapper {
            companion object {
                fun x() = 0
            }
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope["Inner"].getField("tested").valueType!!
        assertEquals(IntType, actualType)
    }

    @Test
    fun testExplicitCompanionForImported() {
        val code = """
        import helper011.Wrapper.Companion.x
        class Inner {
            val tested = x()
        }
        
        package helper011
        class Wrapper {
            companion object {
                fun x() = 0
            }
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope["Inner"].getField("tested").valueType!!
        assertEquals(IntType, actualType)
    }

    @Test
    fun testExplicitCompanionForImported2() {
        val code = """
        import helper012.Wrapper.Companion
        class Inner {
            val tested = Companion.x()
        }
        
        package helper012
        class Wrapper {
            companion object {
                fun x() = 0
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
        import helper005m.Wrapper.x
        class Inner {
            val tested = x()
        }
        
        package helper005m
        class Wrapper {
            companion object {
                fun x() = 0
            }
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope["Inner"].getField("tested").valueType!!
        assertEquals(IntType, actualType)
    }

    @Test
    fun testMultipleFunctionsInBodyAvailable() {
        val code = """
        fun method(): Int {
            fun a() = b()
            fun b() = 0
            val tested = a()
            return tested
        }
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope.firstChild(ScopeType.METHOD)
            .firstChild(ScopeType.METHOD_BODY)
            .getField("tested").valueType!!
        assertEquals(IntType, actualType)
    }

    @Test
    fun testMultipleFunctionsInBodyUnavailable() {
        assertThrows<IllegalStateException> {
            val code = """
            fun method(): Int {
                fun a() = b()
                val tested = a()
                fun b() = 0 // b may depend on tested, and is placed in a sub-scope, so unavailable
                return tested
            }
            """.trimIndent()
            typeResolveScope(code)
        }
    }

    @Test
    fun testMethodInPackageScope() {
        val code = """
        class A
        fun target(p: A): Int
        val tested = target(A())
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope.getField("tested").valueType!!
        assertEquals(IntType, actualType)
    }

    @Test
    fun testBestMatchIsChosen() {
        // todo implement matching logic...
        //  what should we prefer???
        //  a) earlier scopes (iteration order)
        //  b) better self-type matches (iteration order)
        val code = """
        open class A
        open class B: A()
        class C: B()
        
        fun method(p: A) = 0L
        fun method(p: C) = 0
        fun method(p: B) = 0f
            
        val tested = method(C())
        """.trimIndent()
        val scope = typeResolveScope(code)
        val actualType = scope.getField("tested").valueType!!
        assertEquals(IntType, actualType)
    }

}