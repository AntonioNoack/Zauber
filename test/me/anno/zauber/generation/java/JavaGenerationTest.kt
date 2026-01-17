package me.anno.zauber.generation.java

import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.ZauberASTBuilder
import me.anno.zauber.expansion.DefaultParameterExpansion.createDefaultParameterFunctions
import me.anno.zauber.tokenizer.ZauberTokenizer
import me.anno.zauber.typeresolution.TypeResolution.resolveTypesAndNames
import me.anno.zauber.typeresolution.TypeResolutionTest.Companion.ctr
import me.anno.zauber.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import me.anno.zauber.types.specialization.Specialization.Companion.noSpecialization
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JavaGenerationTest {

    companion object {
        fun testClassGeneration(code: String): String {
            val testScopeName = "test${ctr++}"
            val tokens = ZauberTokenizer(
                """
            package $testScopeName
            
            $code
        """.trimIndent(), "?"
            ).tokenize()
            ZauberASTBuilder(tokens, root).readFileLevel()
            createDefaultParameterFunctions(root)
            val testScope = root.children.first { it.name == testScopeName }
            resolveTypesAndNames(testScope)
            val testClassName = "Test"
            val testClass = testScope.children.first { it.name == testClassName }
            val sourceCode = JavaSourceGenerator.run {
                generateInside(testClassName, testClass, noSpecialization)
                if (builder.endsWith('\n')) builder.setLength(builder.length - 1)
                finish()
            }
            return sourceCode
        }

        fun testClassGenIsFine(code: String): String {
            val code = testClassGeneration(code)
            check("Exception" !in code && "Error" !in code) { code }
            return code
        }

        fun testExprGenIsFine(code: String): String {
            val code = testClassGeneration("class Test {\n$code\n}")
            check("Exception" !in code && "Error" !in code) { code }
            return code
        }
    }

    @Test
    fun testSimpleClass() {
        val source = """
            class Test()
        """.trimIndent()
        val expected = """
            public class Test {
              public Test() {}
            }
        """.trimIndent()
        assertEquals(expected, testClassGeneration(source))
    }

    @Test
    fun testPrimaryConstructorCode() {
        val source1 = """
            class Test {
                val x: Int = 5
            }
        """.trimIndent()
        val source2 = """
            class Test {
                val x: Int
                init {
                    x = 5
                }
            }
        """.trimIndent()
        val actual1 = testClassGeneration(source1)
        val actual2 = testClassGeneration(source2)
        println("Generated1:\n$actual1")
        println("Generated2:\n$actual2")
        assertEquals(actual2, actual1)
        assertTrue("x = " in actual1 && " = 5" in actual1)
    }

    @Test
    fun testSimpleObject() {
        val source = """
            object Test
        """.trimIndent()
        val expected = """
            public final class Test {
              private static final Test __instance__ = new Test();
              private Test() {}
            }
        """.trimIndent()
        assertEquals(expected, testClassGeneration(source))
    }

    @Test
    fun testGeneratesDataClass() {
        // todo this test is much too complicated, but maybe we can test the main components?
        //  that sounds like a good plan :)
        val source = """
            data class Test(val x: Int, val y: Long)
            
            // utilities in standard library necessary for generation:
            package zauber
            class Any {
                open fun hashCode(): Int = 0
                open fun toString(): String = ""
                open fun equals(other: Any?): Boolean = this === other
            }
            class String {
                fun plus(other: Any?): String
            }
            class Int {
                fun plus(other: Int): Int
                fun times(other: Int): Int
            }
            class Long {}
        """.trimIndent()
        val expected = """
            public final class Test {
              public final int x;
              public final long y;
              public Test(int x, long y) {
                this.x = x;
                this.y = y;
              }
              @Override
              public String toString() {
                return "Test(x=" + x + ",y=" + y + ")";
              }
              @Override
              public int hashCode() {
                return x.hashCode() * 31 + y.hashCode();
              }
              @Override
              public boolean equals(Object o) {
                return o instanceof Test &&
                    o.x == x &&
                    o.y == y;
              }
            }
        """.trimIndent()
        assertEquals(expected, testClassGeneration(source))
    }

    @Test
    fun testStackOverflowIssue() {
        // fix the crash cause...
        //  -> if else was accidentally recursive in ASTSimplifier
        val code = testClassGeneration(
            $$"""
        open class S(val dst: Any?)
        class Test(dst: Any?, val left: Any?, val right: Any?, val negated: Boolean): S(dst) {
            override fun toString(): String {
                return "$dst = $left ${if (negated) "!=" else "=="} $right"
            }
        }
        
        external fun String.plus(other: Any?): String
        
        package zauber
        class String
            """.trimIndent()
        )
        check("Exception" !in code && "Error" !in code) { code }
    }

    @Test
    fun testFieldResolution() {
        val code = testClassGeneration(
            """
            data class Test<First, Second>(val first: First, val second: Second)

            infix fun <F, S> F.to(other: S): Test<F, S> = Test(this, other)
            
            external fun Int.plus(other: Int): Int
            external fun Int.times(other: Int): Int
            external fun String.plus(other: Any?): String
            
            package zauber
            class Int
            class String
        """.trimIndent()
        )
        check("Exception" !in code && "Error" !in code) { code }
    }

    @Test
    fun testDataClass() {
        val code = testClassGeneration(
            """
            data class Test<V>(val index: Int, val value: V)
            
            external fun Int.plus(other: Int): Int
            external fun Int.times(other: Int): Int
            external fun String.plus(other: Any?): String
            
            package zauber
            class Int
            class String
        """.trimIndent()
        )
        check("Exception" !in code && "Error" !in code) { code }
        check("int index;" in code) { code }
        check("V value;" in code) { code }
    }

    @Test
    fun testRunApply() {
        val code = testClassGeneration(
            """
            class Test() {
                fun <R> run(runnable: () -> R): R {
                    return runnable()
                }
            }
        """.trimIndent()
        )
        check("Exception" !in code && "Error" !in code) { code }
    }

    @Test
    fun testRunWithReturnMismatch() {
        val code = testClassGeneration(
            """
            class Test() {
                fun runImpl(runnable: () -> Unit) {
                    runnable()
                }
                fun test() {
                    runImpl {
                        "Some Value"
                    }
                }
            }
            
            package zauber
            fun interface Function0<R> {
                fun call(): R
            }
        """.trimIndent()
        )
        check("Exception" !in code && "Error" !in code) { code }
    }


    @Test
    fun testCompareOperators() {
        testTypeResolution(
            """
                val tested = 0
                package zauber
                class Int {
                    fun compareTo(other: Int): Int
                }
            """.trimIndent()
        )
        testExprGenIsFine("val tested = 0 < 1")
        testExprGenIsFine("val tested = 0 <= 1")
        testExprGenIsFine("val tested = 0 > 1")
        testExprGenIsFine("val tested = 0 >= 1")
        testExprGenIsFine("val tested = 0 == 1")
        testExprGenIsFine("val tested = 0 != 1")
        testExprGenIsFine("val tested = 0 === 1")
        testExprGenIsFine("val tested = 0 !== 1")
    }

    @Test
    fun testCompareOperatorsMixed() {
        testTypeResolution(
            """
                val tested = 0
                package zauber
                class Float {
                    fun compareTo(other: Int): Int
                }
            """.trimIndent()
        )
        testExprGenIsFine("val tested = 0f < 1")
        testExprGenIsFine("val tested = 0f <= 1")
        testExprGenIsFine("val tested = 0f > 1")
        testExprGenIsFine("val tested = 0f >= 1")
        testExprGenIsFine("val tested = 0f == 1")
        testExprGenIsFine("val tested = 0f != 1")
        testExprGenIsFine("val tested = 0f === 1")
        testExprGenIsFine("val tested = 0f !== 1")
    }

    @Test
    fun testMethodGeneration() {
        testClassGenIsFine(
            """
            class Test(val value: Double) {
                operator fun plus(other: Int): Double = plus(other.toLong())
                external operator fun plus(other: Long): Double

                external fun toInt(): Int
                external fun toLong(): Long
            }
            
            package zauber
            class Long
            class Int {
                external fun toLong(): Long
            }
        """.trimIndent()
        )
    }

    @Test
    fun testInlineFunction() {
        testClassGenIsFine(
            """
            class Test {
                inline fun map(v: Int, x: (Int) -> Int) {
                    return x(v)
                }
                
                fun calculate(): Int {
                    return map(7, { it * it })
                }
            }
        """.trimIndent()
        )
    }

    @Test
    fun testGenerateLambda() {
        testClassGenIsFine(
            """
            interface Iterator<V> {
                fun next(): V
                fun hasNext(): Boolean
            }
            interface Test<V> {
                operator fun iterator(): Iterator<V>

                fun all(predicate: (V) -> Boolean): Boolean {
                    for (element in this) {
                        if (!predicate(element)) return false
                    }
                    return true
                }
            }
            
            package zauber
            class Boolean {
                external fun not(): Boolean
            }
            fun interface Function1<V,R> {
                fun call(p0: V): R
            }
        """.trimIndent()
        )
    }

    @Test
    fun testInlineWithoutLambdas() {
        testClassGenIsFine(
            """
            class Test {
                inline fun add(a: Int, b: Int): Int = a + b
                fun calculate(c: Int, d: Int, e: Int): Int {
                    return add(c,add(d,e))
                }
            }
            
            package zauber
            class Int {
                external fun plus(other: Int): Int
            }
        """.trimIndent()
        )
    }

    @Test
    fun testInlineWithLambda() {
        val code = testClassGenIsFine(
            """
            class Test {
                inline fun x6(runnable: () -> Unit) {
                    runnable()
                    runnable()
                    runnable()
                    runnable()
                    runnable()
                    runnable()
                }
                fun calculate(a: Int): Int {
                    x6 {
                        println(a)
                    }
                }
            }
            
            package zauber
            external fun println(a: Int)
            fun interface Function0<R> {
                fun call(): R
            }
        """.trimIndent()
        )
        val countPrintlns = code.split("println(").size
        check(countPrintlns >= 6) {
            "Too few printlns? $code"
        }
    }

    @Test
    fun testInlineWithLambdaWithSelf() {
        testClassGenIsFine(
            """
            class Test {
                inline fun Int.x4(runnable: Int.() -> Unit) {
                    runnable()
                    runnable()
                    runnable()
                    runnable()
                }
                fun calculate(a: Int): Int {
                    a.x4 {
                        println(this)
                    }
                }
            }
            
            package zauber
            external fun println(a: Int)
            class Int
        """.trimIndent()
        )
    }
}