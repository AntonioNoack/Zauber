package me.anno.generation.java

import me.anno.utils.ResolutionUtils.ctr
import me.anno.utils.ResolutionUtils.testTypeResolution
import me.anno.zauber.Compile.root
import me.anno.zauber.SpecialFieldNames.OBJECT_FIELD_NAME
import me.anno.zauber.ast.rich.ZauberASTClassScanner.Companion.scanClasses
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.tokenizer.ZauberTokenizer
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Specialization.Companion.noSpecialization
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenerationTest {

    companion object {
        fun testClassGeneration(code: String): String {
            val testScopeName = "test${ctr++}"
            val tokens = ZauberTokenizer(
                """
            package $testScopeName
            
            $code
        """.trimIndent(), "Test.zbr"
            ).tokenize()
            scanClasses(tokens)
            val testScope = root.children.first { it.name == testScopeName }
            val testClassName = "Test"
            val testClass = testScope[ScopeInitType.AFTER_DISCOVERY].children.first { it.name == testClassName }

            val gen = JavaSourceGenerator()
            val builder = gen.builder
            gen.appendClass(
                testClassName, testClass, noSpecialization,
                testClass.methods0.map { Specialization(it.memberScope, emptyParameterList()) },
                testClass.fields.map { Specialization(it.fieldScope, emptyParameterList()) }, false
            )
            if (builder.endsWith('\n')) builder.setLength(builder.length - 1)
            return gen.finish()
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
            public final class Test {
              
              public Test() {
                super();
                {
                }
              }
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
        // todo disable type comments...
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
  public static final Test $OBJECT_FIELD_NAME = new Test();
  
  
  private Test() {
    // no super call
    {
    }
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
        check("int index;" in code) { "Missing 'int index;' in $code" }
        check("V value;" in code) { "Missing V value; in $code" }
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
                    fun equals(other: Int): Boolean
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
                    fun equals(other: Int): Boolean
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
            
            package zauber
            fun interface Function1<P0, R> {
                fun call(p0: P0): R
            }
            class Int {
                external operator fun times(other: Int): Int
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
            enum class Boolean {
                TRUE, FALSE;
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
            fun interface Function1<P0, R> {
                fun call(p0: P0): R
            }
            class Int
        """.trimIndent()
        )
    }
}