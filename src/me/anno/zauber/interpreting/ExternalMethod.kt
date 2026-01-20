package me.anno.zauber.interpreting

fun interface ExternalMethod {
    fun process(runtime: Runtime, self: Instance?, parameters: List<Instance>): Instance
}