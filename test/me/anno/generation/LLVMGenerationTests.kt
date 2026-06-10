package me.anno.generation

import me.anno.compilation.MinimalCompiler
import me.anno.compilation.MinimalLLVMCompiler
import me.anno.zauber.logging.LogManager
import org.junit.jupiter.api.Test

// todo we need more tests:
//  - try-catch
//  - yield

/**
 * Execution speed: ~1.0s
 * */
class LLVMGenerationTests : CodeGenerationTests() {

    override fun registerLib() {
        CGenerationTests().registerLib()
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
        LogManager.enable("ASTSimplifier")
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
    fun testNumberNegation() {
        testNumberNegationImpl()
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
    fun testLogicalOperators() {
        testLogicalOperatorsImpl()
    }

    @Test
    fun testInstanceOf() {
        testInstanceOfImpl()
    }

    @Test
    fun testStringOps() {
        testStringOpsImpl()
    }

    // todo test all logical (and, or, xor, inv) and all shift (shl, shr, ushr) operations
    //  for all relevant number types...

    @Test
    fun testUseNativeLibrary() {
        TODO("Call into a native library")
    }

}