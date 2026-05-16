package me.anno.generation.rust

import me.anno.compilation.MinimalRustCompiler
import me.anno.generation.CodeGenerationTests
import me.anno.generation.java.JavaSourceGenerator.Companion.register
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Test

/**
 * execution time: ~10s for all
 * */
class RustGenerationTests : CodeGenerationTests() {

    override fun registerLib() {
        register(
            langScope, "println", listOf(Types.Int),
            "println!(\"{}\", arg0)"
        )
    }

    override fun generator() = MinimalRustCompiler()

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