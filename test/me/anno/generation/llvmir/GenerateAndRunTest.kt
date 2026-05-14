package me.anno.generation.llvmir

import me.anno.compilation.MinimalCompiler
import me.anno.compilation.MinimalLLVMCompiler
import me.anno.generation.CodeGenerationTests
import me.anno.generation.LoggerUtils.disableCompileLoggers
import me.anno.generation.java.JavaSourceGenerator.Companion.register
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.Types
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GenerateAndRunTest : CodeGenerationTests() {

    override fun registerLib() {
        register(
            langScope, "println", listOf(Types.Int),
            "System.out.println(arg0)" // todo adjust this
        )
    }

    override fun generator(): MinimalCompiler = MinimalLLVMCompiler()

    @BeforeEach
    fun init() {
        disableCompileLoggers()
    }

    @Test
    fun testSimpleAddition() {
        testSimpleAdditionImpl()
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

    // todo implement value classes being copied when written / passed as parameter
    // todo implement and test value classes being inlined

    // todo implement and test working with strings
    // todo test specialized classes being usable

}