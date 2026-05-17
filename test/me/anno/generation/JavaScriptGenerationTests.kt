package me.anno.generation

import me.anno.compilation.MinimalCompiler
import me.anno.compilation.MinimalJavaScriptCompiler
import me.anno.generation.java.JavaSourceGenerator
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Test

/**
 * execution time: ~0.5s for all
 * */
class JavaScriptGenerationTests : CodeGenerationTests() {

    override fun registerLib() {
        JavaSourceGenerator.register(
            TypeResolution.langScope, "println", listOf(Types.Int),
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