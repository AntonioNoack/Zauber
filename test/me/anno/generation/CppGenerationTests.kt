package me.anno.generation

import me.anno.compilation.MinimalCppCompiler
import me.anno.generation.java.JavaSourceGenerator
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Types
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * execution time: ~3s for all
 * */
class CppGenerationTests : CodeGenerationTests() {

    override fun registerLib() {
        JavaSourceGenerator.register(
            TypeResolution.langScope, "println", listOf(Types.Int),
            "#include <stdio.h>\n" +
                    "printf(\"%d\\n\",arg0)"
        )
    }

    override fun generator() = MinimalCppCompiler()

    @BeforeEach
    fun init() {
        // disableCompileLoggers()
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

    // todo implement and test working with strings
    // todo test specialized classes being usable

}