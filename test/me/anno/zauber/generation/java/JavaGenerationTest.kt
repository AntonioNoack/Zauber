package me.anno.zauber.generation.java

import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.ASTBuilder
import me.anno.zauber.expansion.DefaultParameterExpansion.createDefaultParameterFunctions
import me.anno.zauber.tokenizer.ZauberTokenizer
import me.anno.zauber.typeresolution.TypeResolution.resolveTypesAndNames
import me.anno.zauber.typeresolution.TypeResolutionTest.Companion.ctr
import org.junit.jupiter.api.Assertions.assertEquals
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
            ASTBuilder(tokens, root).readFileLevel()
            createDefaultParameterFunctions(root)
            val testScope = root.children.first { it.name == testScopeName }
            resolveTypesAndNames(testScope)
            val testClassName = "Test"
            val testClass = testScope.children.first { it.name == testClassName }
            val sourceCode = JavaSourceGenerator.run {
                generateInside(testClassName, testClass)
                if (builder.endsWith('\n')) builder.setLength(builder.length - 1)
                finish()
            }
            return sourceCode
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
}