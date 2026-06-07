package me.anno.generation

import me.anno.compilation.MinimalCompiler
import me.anno.compilation.MinimalPythonCompiler
import me.anno.generation.java.JavaSourceGenerator
import me.anno.generation.python.PythonSourceGenerator.Companion.nativePythonNumbers
import me.anno.zauber.typeresolution.TypeResolution
import org.junit.jupiter.api.Test

/**
 * execution time: ~0.4s for all
 * */
class PythonGenerationTests : CodeGenerationTests() {

    override fun registerLib() {
        for ((type, _) in nativePythonNumbers) {
            JavaSourceGenerator.register(
                TypeResolution.langScope, "println", listOf(type),
                "print(arg0)"
            )
        }
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
    fun testUseNativeLibrary() {
        TODO("Call into a native library")
    }

    // todo somehow describe and connect to a Python library
    // todo should we implement weird number comparisons? could be useful...
    //  like Int and UInt, Float and Long, etc...
    //  e.g. Int vs UInt must look at the sign, too
    // to do disable specialization, where not necessary???

}