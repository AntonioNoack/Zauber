package me.anno.cpp.ast.rich

import me.anno.cpp.tokenizer.CppTokenizer
import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.ZauberASTBuilder
import me.anno.zauber.expansion.DefaultParameterExpansion.createDefaultParameterFunctions
import me.anno.zauber.tokenizer.ZauberTokenizer
import me.anno.zauber.typeresolution.TypeResolution.resolveTypesAndNames
import me.anno.zauber.typeresolution.TypeResolutionTest.Companion.ctr
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Types.UnitType
import org.junit.jupiter.api.Test

class ParsingTest {

    companion object {
        fun testCppParsing(code: String): Scope {
            val testScopeName = "test${ctr++}"
            val raw = """
            namespace $testScopeName {
            $code
            }
        """.trimIndent()

            val tokens = CppTokenizer(raw, "main.c", false).tokenize()
            // println("Tokens: $tokens")

            // todo run the preprocessor?

            CppASTBuilder(tokens, root, CppStandard.CPP11).readFile()
            createDefaultParameterFunctions(root)
            val testScope = root.children.first { it.name == testScopeName }
            resolveTypesAndNames(testScope)
            return testScope
        }

        fun ensureUnitIsKnown() {
            // todo how can we avoid duplicate assignments???
            val tokens = ZauberTokenizer(
                """
            package zauber
            object Unit
        """.trimIndent(), "?"
            ).tokenize()
            ZauberASTBuilder(tokens, root).readFileLevel()
        }
    }

    @Test
    fun testSimpleVariable() {
        testCppParsing(
            """
            int tested = 0;
        """.trimIndent()
        )
    }

    @Test
    fun testParsingEnum() {
        testCppParsing(
            """
            enum Color {
                RED = 1, YELLOW, GREEN = 0, BLUE = 0
            }
        """.trimIndent()
        )
    }

    @Test
    fun testParsingClass() {
        testCppParsing(
            """
            class Color {
                int x;
                int y = 0;
            }
        """.trimIndent()
        )
    }

    @Test
    fun testParsingStruct() {
        testCppParsing(
            """
            class Color {
                int x;
                int y = 0;
            }
        """.trimIndent()
        )
    }

    @Test
    fun testMethod() {
        ensureUnitIsKnown()
        testCppParsing(
            """
            void main(int a, int b, int c) {
                return;
            }
        """.trimIndent()
        )
    }

    @Test
    fun testVoidArgsMethod() {
        ensureUnitIsKnown()
        testCppParsing(
            """
            void main(void) {
                return;
            }
        """.trimIndent()
        )
    }
}