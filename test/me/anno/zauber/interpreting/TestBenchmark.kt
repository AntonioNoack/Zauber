package me.anno.zauber.interpreting

// 1ms/run -> not too bad for creating a runtime each time
fun main() {
    val t0 = System.nanoTime()
    val instance = TestRuntime()
    instance.init()

    val runs = 1000
    repeat(runs) {
        instance.testFactorialAsWhileLoop()
    }
    val t1 = System.nanoTime()
    println("Took ${((t1 - t0) / (1e6f * runs))} ms / run")
}