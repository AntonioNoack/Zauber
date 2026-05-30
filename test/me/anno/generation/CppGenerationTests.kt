package me.anno.generation

import me.anno.compilation.MinimalCppCompiler
import me.anno.generation.java.JavaSourceGenerator
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Test

/**
 * execution time: 4.1s
 * ~2s for all when preserveFolder=true, instead of 3s
 * */
class CppGenerationTests : CodeGenerationTests() {

    override fun registerLib() {
        for (type in listOf(Types.Byte, Types.Short, Types.Int)) {
            JavaSourceGenerator.register(
                TypeResolution.langScope, "println", listOf(type),
                "#include <stdio.h>\n" +
                        "printf(\"%d\\n\",arg0)"
            )
        }
        for (type in listOf(Types.UByte, Types.UShort, Types.UInt)) {
            JavaSourceGenerator.register(
                TypeResolution.langScope, "println", listOf(type),
                "#include <stdio.h>\n" +
                        "printf(\"%u\\n\",arg0)"
            )
        }

        JavaSourceGenerator.register(
            TypeResolution.langScope, "println", listOf(Types.Long),
            "#include <stdio.h>\n" +
                    "printf(\"%ld\\n\",arg0)"
        )
        JavaSourceGenerator.register(
            TypeResolution.langScope, "println", listOf(Types.ULong),
            "#include <stdio.h>\n" +
                    "printf(\"%lu\\n\",arg0)"
        )

        for (type in listOf(Types.Float, Types.Double)) {
            JavaSourceGenerator.register(
                TypeResolution.langScope, "println", listOf(type),
                "#include <stdio.h>\n" +
                        "printf(\"%f\\n\",arg0)"
            )
        }
    }

    override fun generator() = MinimalCppCompiler(true)

    @Test
    fun testSimpleMath() {
        testSimpleMathImpl()
    }

    @Test
    fun testMethodCall() {
        testMethodCallImpl()
    }

    @Test
    fun testDataClassAndAllocation() {
        testDataClassAndAllocationImpl()
    }

    @Test
    fun testGenericClass() {
        testGenericClassImpl()
    }

    @Test
    fun testValueClassFieldIsWritable() {
        testValueClassFieldIsWritableImpl()
    }

    @Test
    fun testValueIsPassedByCopy() {
        testValueIsPassedByCopyImpl()
    }

    @Test
    fun testSimpleBranch() {
        testSimpleBranchImpl()
    }

    @Test
    fun testSimpleLoop() {
        testSimpleLoopImpl()
    }

    @Test
    fun testIntArray() {
        testIntArrayImpl()
    }

    @Test
    fun testReferenceArray() {
        testReferenceArrayImpl()
    }

    @Test
    fun testClassInheritance() {
        testClassInheritanceImpl()
    }

    @Test
    fun testInterfaceInheritance() {
        testInterfaceInheritanceImpl()
    }

    @Test
    fun testNumberOverflows() {
        testNumberOverflowsImpl()
    }

    @Test
    fun testNumberComparisons() {
        testNumberComparisonsImpl()
    }

    @Test
    fun testNumberConversions() {
        testNumberConversionsImpl()
    }

    @Test
    fun testNonNumberComparisons() {
        testNonNumberComparisonsImpl()
    }

    @Test
    fun testUseNativeLibrary() {
        TODO("Call into a native library")
    }

    // todo implement and test working with strings
    // todo test specialized classes being usable

}