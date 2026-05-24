package me.anno.generation

import me.anno.compilation.MinimalCppCompiler
import me.anno.generation.java.JavaSourceGenerator
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Types
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * execution time: 4.1s
 * ~2s for all when preserveFolder=true, instead of 3s
 * */
class CppGenerationTests : CodeGenerationTests() {

    override fun registerLib() {
        JavaSourceGenerator.register(
            TypeResolution.langScope, "println", listOf(Types.Int),
            "#include <stdio.h>\n" +
                    "printf(\"%d\\n\",arg0)"
        )
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

    // todo implement and test working with strings
    // todo test specialized classes being usable

}