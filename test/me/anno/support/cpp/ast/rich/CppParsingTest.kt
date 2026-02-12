package me.anno.support.cpp.ast.rich

import me.anno.support.cpp.tokenizer.CppTokenizer
import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.ZauberASTBuilder
import me.anno.zauber.expansion.DefaultParameterExpansion.createDefaultParameterFunctions
import me.anno.zauber.tokenizer.ZauberTokenizer
import me.anno.zauber.typeresolution.TypeResolution.resolveTypesAndNames
import me.anno.zauber.typeresolution.TypeResolutionTest.Companion.ctr
import me.anno.zauber.types.Scope
import org.junit.jupiter.api.Test

class CppParsingTest {

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

            CppASTBuilder(tokens, root, CppStandard.CPP11).readFileLevel()
            createDefaultParameterFunctions(root)
            val testScope = root.children.first { it.name == testScopeName }
            resolveTypesAndNames(testScope)
            return testScope
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
        testCppParsing(
            """
            void main(void) {
                return;
            }
        """.trimIndent()
        )
    }

    @Test
    fun testSimpleSwitchCase() {
        testCppParsing(
            """
            void test(int x) {
                switch(x) {
                    case 0:
                        test(x+1);
                    case 1:
                    case 2:
                        break;
                    default:
                        break;
                }
            }
        """.trimIndent()
        )
    }

    @Test
    fun testDeclarationInMethod() {
        testCppParsing(
            """
            void main(void) {
                int x = 0;
            }
        """.trimIndent()
        )
    }

    @Test
    fun testCallInMethod() {
        testCppParsing(
            """
            void main(void) {
                main();
            }
        """.trimIndent()
        )
    }

    @Test
    fun testAssignmentInMethod() {
        testCppParsing(
            """
            int x;
            void main(void) {
                x = 5;
            }
        """.trimIndent()
        )
    }

    @Test
    fun testDeepAssignmentInMethod() {
        testCppParsing(
            """
            struct Vec {
                int x;
            };
            void main(void) {
                struct Vec v;
                v.x = 5;
            }
        """.trimIndent()
        )
    }

    @Test
    fun testEvilSwitchCase() {
        testCppParsing(
            """
            void test(int x) {
                switch(x) {
                // yes, this is allowed
                    int y;
                case 0:
                    test(x+1);
                case 1:
                    break;
                case 2:
                default:
                    break;
                }
            }
        """.trimIndent()
        )
    }
}