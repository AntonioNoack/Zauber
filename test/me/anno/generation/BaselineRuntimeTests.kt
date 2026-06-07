package me.anno.generation

import me.anno.compilation.RuntimeCompiler
import me.anno.zauber.logging.LogManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * execution time: ~250 ms
 * */
class BaselineRuntimeTests : CodeGenerationTests() {

    override fun registerLib() {}

    @BeforeEach
    fun init() {
        LogManager.disable("Runtime,Stdlib")
    }

    // todo complete test of all basic number methods (+,-,*,/,%,shl,shr,ushr,and,or,xor,inv)

    override fun generator() = RuntimeCompiler()

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
    fun testNumberConversionsBenchmark() {
        LogManager.disable("ResolutionUtils,MinimalCompiler")
        for (i in 0 until 10) {
            val t0 = System.nanoTime()
            try {
                testNumberConversionsImpl()
            } catch (e: Exception) {
            }
            val t1 = System.nanoTime()
            println("Run $i, ${(t1 - t0) * 1e-6f } ms")
        }
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
    fun testUseNativeLibrary() {
        TODO("Call into a native library")
    }

}