package me.anno

import me.anno.utils.ResolutionUtils.testTypeResolution
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.Instance
import me.anno.zauber.types.Type
import org.junit.jupiter.api.Assertions.assertEquals

class MultiTest {

    private val runnables = HashMap<String, () -> Unit>()

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

    fun run(name: String) {
        runnables[name]!!()
    }
}