package me.anno

import me.anno.generation.cpp.CppGenerationTests
import me.anno.generation.java.JavaGenerationTests
import me.anno.generation.javascript.JavaScriptGenerationTests
import me.anno.generation.llvmir.LLVMGenerationTests
import me.anno.generation.rust.RustGenerationTests
import me.anno.generation.wasm.WASMGenerationTests
import me.anno.utils.ResolutionUtils.testTypeResolution
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.Instance
import me.anno.zauber.types.Type
import org.junit.jupiter.api.Assertions.assertEquals

class MultiTest {

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

    fun type(code: String, getType: () -> Type): MultiTest {
        return or("type") {
            val actual = testTypeResolution(code, reset = true)
            assertEquals(getType(), actual)
        }
    }

    fun runtime(code: String, validateInstance: (Instance) -> Unit): MultiTest {
        return or("runtime") {
            val instance = testExecute(code)
            validateInstance(instance)
        }
    }

    fun compile(code: String, expected: String): MultiTest {
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