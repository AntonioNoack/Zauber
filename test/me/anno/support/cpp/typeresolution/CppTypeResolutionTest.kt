package me.anno.support.cpp.typeresolution

import me.anno.support.cpp.ast.rich.CppASTBuilder
import me.anno.support.cpp.ast.rich.CppParsingTest.Companion.testCppParsing
import me.anno.support.cpp.ast.rich.CppStandard
import me.anno.support.cpp.ast.rich.PointerType.Companion.ptr
import me.anno.support.cpp.tokenizer.CppTokenizer
import me.anno.zauber.Compile.root
import me.anno.zauber.expansion.DefaultParameterExpansion.createDefaultParameterFunctions
import me.anno.zauber.resolution.ResolutionUtils.getField
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution.resolveTypesAndNames
import me.anno.zauber.typeresolution.TypeResolutionTest.Companion.ctr
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.IntType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CppTypeResolutionTest {

    companion object {

        fun testCppTypeResolution(code: String): Type {
            val testScopeName = "test${ctr++}"
            val raw = """
            namespace $testScopeName {
            $code
            }
        """.trimIndent()

            val tokens = CppTokenizer(raw, "main.c", false).tokenize()

            // todo run the preprocessor?

            CppASTBuilder(tokens, root, CppStandard.CPP11).readFileLevel()
            createDefaultParameterFunctions(root)
            val testScope = root.children.first { it.name == testScopeName }
            resolveTypesAndNames(testScope)
            val field = testScope.fields.first { it.name == "tested" }
            return field.valueType
                ?: throw IllegalStateException("Could not resolve type for $field")
        }

    }

    @Test
    fun testSimpleVariable() {
        assertEquals(IntType, testCppTypeResolution("int tested;"))
    }

    @Test
    fun testPointerLeft() {
        assertEquals(IntType.ptr(), testCppTypeResolution("int* tested;"))
    }

    @Test
    fun testPointerRight() {
        assertEquals(IntType.ptr(), testCppTypeResolution("int *tested;"))
    }

    @Test
    fun testPointerAttachment() {
        val scope = testCppParsing(
            """
            int *ptr, single;
            int* singlePtr, **ptr3;
        """.trimIndent()
        )
        val context = ResolutionContext(null, false, null)
        assertEquals(IntType.ptr(), scope.getField("ptr").resolveValueType(context))
        assertEquals(IntType, scope.getField("single").resolveValueType(context))
        assertEquals(IntType.ptr(), scope.getField("singlePtr").resolveValueType(context))
        assertEquals(IntType.ptr().ptr().ptr(), scope.getField("ptr3").resolveValueType(context))
    }
}