package me.anno.generation

import me.anno.compilation.MinimalCompiler
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.expansion.DependencyData
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.createTestRuntime
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Types
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * execution time: ~230 ms
 * */
class BaselineRuntimeTests : CodeGenerationTests() {

    class RuntimeCompiler : MinimalCompiler() {
        private lateinit var data: DependencyData
        private lateinit var mainMethod: Method

        override fun compile(
            projectFolder: File,
            srcFolder: File,
            dependencies: DependencyData,
            mainMethod: Method
        ) {
            this.data = dependencies
            this.mainMethod = mainMethod
        }

        override fun execute(projectFolder: File): String {
            val runtime = runtime

            createTestRuntime() // register testing methods

            // register printing to buffer, so we can keep track of it
            val buffer = StringBuilder()
            runtime.register(langScope, "println", listOf(Types.Int)) { _, params ->
                buffer.append(params[0].castToInt()).append('\n')
                runtime.getUnit()
            }

            val main = mainMethod
            val self = runtime.getObjectInstance(main.ownerScope.typeWithArgs)
            val spec = Specialization(main.memberScope, emptyParameterList())
            val result = runtime.executeCall(self, null, spec, emptyList())
            check(result.type == ReturnType.RETURN) { "Call failed: $result" }
            return buffer.toString()
        }
    }

    override fun registerLib() {}

    @BeforeEach
    fun init() {
        LogManager.disable("Runtime,Stdlib")
    }

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

}