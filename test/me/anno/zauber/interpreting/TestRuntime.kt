package me.anno.zauber.interpreting

import me.anno.cpp.ast.rich.CppParsingTest.Companion.ensureUnitIsKnown
import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.ZauberASTBuilder
import me.anno.zauber.tokenizer.ZauberTokenizer
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.TypeResolutionTest.Companion.ctr
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.StringType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TestRuntime {
    companion object {
        fun testExecute(code: String): Pair<Runtime, Instance> {
            val runtime = Runtime()
            val testScopeName = "test${ctr++}"
            val tokens = ZauberTokenizer(
                """
                package $testScopeName
                
                $code
            """.trimIndent(), "Main.zbr"
            ).tokenize()
            ZauberASTBuilder(tokens, root).readFileLevel()
            TypeResolution.resolveTypesAndNames(root)
            val scope = root.children.first { it.name == testScopeName }
            val field = scope.fields.first { it.name == "tested" }
            // val context = ResolutionContext(field.codeScope, null, false, null)
            // val simplified = ASTSimplifier.simplify(context, field.initialValue!!)
            val getter = field.getter
                ?: throw IllegalStateException("Missing getter for $field")
            val value = runtime.executeCall(runtime.getNull(), getter, emptyList())
            return runtime to value
        }
    }

    @Test
    fun testStringField() {
        ensureUnitIsKnown()
        val code = """
            val tested = "Some String"
        """.trimIndent()
        val (_, value) = testExecute(code)
        assertEquals(StringType, value.type.type)
        // todo check content somehow...
    }

    @Test
    fun testIntField() {
        ensureUnitIsKnown()
        val code = """
            val tested get() = 1+3*7
            
            package zauber
            class Int {
                external fun plus(other: Int): Int
                external fun times(other: Int): Int
            }
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(IntType, value.type.type)
        assertEquals(22, runtime.castToInt(value))
    }
}