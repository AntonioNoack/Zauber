package me.anno.generation

import me.anno.compilation.MinimalCompiler
import me.anno.compilation.MinimalPythonCompiler
import me.anno.generation.java.JavaSourceGenerator
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Test

/**
 * execution time: ~0.4s for all
 * */
class PythonGenerationTests : CodeGenerationTests() {

    override fun registerLib() {
        JavaSourceGenerator.register(
            TypeResolution.langScope, "println", listOf(Types.Int),
            "print(arg0)"
        )
    }

    override fun generator(): MinimalCompiler = MinimalPythonCompiler()

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

}