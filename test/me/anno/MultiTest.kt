package me.anno

import me.anno.generation.CppGenerationTests
import me.anno.generation.JavaGenerationTests
import me.anno.generation.JavaScriptGenerationTests
import me.anno.generation.LLVMGenerationTests
import me.anno.generation.RustGenerationTests
import me.anno.generation.WASMGenerationTests
import me.anno.utils.ResolutionUtils.testTypeResolution
import me.anno.utils.assertEquals
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.Instance
import me.anno.zauber.types.Type

class MultiTest(val code: String) {

    private val runnables = HashMap<String, () -> Unit>()

    companion object {
        // languages, that work pretty well
        val languages = listOf(
            "c++" to CppGenerationTests(),
            "java" to JavaGenerationTests(),
            "js" to JavaScriptGenerationTests(),
            "wasm" to WASMGenerationTests(),
            "rust" to RustGenerationTests(),
            "llvm" to LLVMGenerationTests(),
        )
    }

    fun or(name: String, runnable: () -> Unit): MultiTest {
        runnables[name] = runnable
        return this
    }

    fun type(getType: () -> Type): MultiTest {
        return or("type") {
            val actual = testTypeResolution(code, reset = true)
            val expected = getType()
            assertEquals(expected, actual) {
                "Got incorrect type"
            }
        }
    }

    fun runtime(validateInstance: (Instance) -> Unit): MultiTest {
        return or("runtime") {
            val instance = testExecute(code)
            validateInstance(instance)
        }
    }

    fun compile(expected: String): MultiTest {
        // todo some languages like Rust should get a per-test folder, or at least per-Rust, so we can cache the dependency builds
        val codeWithMain = if ("fun main(" in code) code else "fun main() { println(tested) }\n$code"
        for ((name, value) in languages) {
            or(name) {
                val actual = value.generator()
                    .testCompileMainAndRun(codeWithMain, true, value::registerLib)
                assertEquals(expected, actual)
            }
        }
        return this
    }

    fun runTest(type: String) {
        val runnable = runnables[type]
            ?: throw IllegalStateException("Missing '$type'")
        runnable()
    }
}