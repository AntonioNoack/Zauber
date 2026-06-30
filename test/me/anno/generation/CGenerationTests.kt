package me.anno.generation

import me.anno.compilation.MinimalCCompiler
import me.anno.utils.assertEquals
import org.junit.jupiter.api.Test

/**
 * execution time: todo measure
 * */
class CGenerationTests : CodeGenerationTests() {

    override fun registerLib() {
        CppGenerationTests().registerLib()
    }

    override fun generator() = MinimalCCompiler()

    @Test
    fun testOperationOrder() {
        testOperationOrderImpl()
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
    fun testBinaryNumberOperations() {
        testBinaryNumberOperationsImpl()
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

    @Test
    fun testUseNativeLibrary() {
        val code = """
            
            @CInclude("<ctype.h>")
            external fun isdigit(c: Char): Int
            
            fun main() {
                println(if(isdigit('A')) 2 else 1)
            }
            
            package zauber
            class Any
            external class Int(val content: Int)
            external class Char(val content: Char)
            
            enum class Boolean { TRUE, FALSE }
            class Array<V>(val size: Int) {
                external fun get(index: Int): V
                external fun set(index: Int, v: V)
                
                fun copyOfRange(i0: Int, i1: Int): Array<V> {
                    val clone = Array<V>(i1-i0)
                    var i = i0
                    while (i < i1) {
                        clone[i - i0] = this[i]
                        i++
                    }
                    return clone
                }
            }
            
            class String
            annotation class CInclude(val source: String)
            
            external fun println(arg0: Int)
        """.trimIndent()

        val printed = generator()
            .testCompileMainAndRun(code, ::registerLib)
        assertEquals("2\n", printed)
    }
}