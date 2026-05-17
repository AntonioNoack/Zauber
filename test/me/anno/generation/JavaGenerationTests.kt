package me.anno.generation

import me.anno.compilation.MinimalJavaBuildCompiler
import me.anno.generation.java.JavaSourceGenerator
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Test

/**
 * execution time: ~7s -> ~0.7s by not using Maven
 * */
class JavaGenerationTests : CodeGenerationTests() {

    override fun registerLib() {
        JavaSourceGenerator.register(
            TypeResolution.langScope, "println", listOf(Types.Int),
            "System.out.println(arg0)"
        )
    }

    override fun generator() = MinimalJavaBuildCompiler()

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

    // todo add .copy(name=value) as a special function on data classes

    // todo implement and test value classes being inlined:
    //  we explode them, and must make their body-functions static...

    // todo implement and test working with strings
    // todo test specialized classes being usable

}