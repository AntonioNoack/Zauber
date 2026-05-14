package me.anno.generation.wasm

import me.anno.compilation.MinimalWASMCompiler
import me.anno.generation.CodeGenerationTests
import me.anno.generation.LoggerUtils.disableCompileLoggers
import me.anno.generation.java.JavaSourceGenerator.Companion.register
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.Types
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * execution time: 2s,
 * main cost is loading Node via NVM, I think
 * */
class WASMGenerationTests : CodeGenerationTests() {

    override fun registerLib() {
        register(
            langScope, "println", listOf(Types.Int),
            "console.log(arg0)"
        )
    }

    override fun generator() = MinimalWASMCompiler()

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

}