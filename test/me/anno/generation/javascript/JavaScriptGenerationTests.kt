package me.anno.generation.javascript

import me.anno.compilation.MinimalCompiler
import me.anno.compilation.MinimalJavaScriptCompiler
import me.anno.generation.CodeGenerationTests
import me.anno.generation.java.JavaSourceGenerator.Companion.register
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Test

/**
 * execution time: ~0.5s for all
 * */
class JavaScriptGenerationTests : CodeGenerationTests() {

    override fun registerLib() {
        register(
            langScope, "println", listOf(Types.Int),
            "console.log(arg0)"
        )
    }

    override fun generator(): MinimalCompiler = MinimalJavaScriptCompiler()

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