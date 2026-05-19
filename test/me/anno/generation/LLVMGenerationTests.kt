package me.anno.generation

import me.anno.compilation.MinimalCompiler
import me.anno.compilation.MinimalLLVMCompiler
import me.anno.generation.java.JavaSourceGenerator
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Test

class LLVMGenerationTests : CodeGenerationTests() {

    override fun registerLib() {
        // todo use these registered functions to generate a .c file
        JavaSourceGenerator.register(
            TypeResolution.langScope, "println", listOf(Types.Int),
            "System.out.println(arg0)" // todo adjust this
        )
    }

    override fun generator(): MinimalCompiler = MinimalLLVMCompiler()

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
    fun testValueArray() {
        testValueArrayImpl()
    }

    @Test
    fun testReferenceArray() {
        testReferenceArrayImpl()
    }

}