package me.anno.zauber.interpreting

fun interface ExternalMethod {
    fun process(self: Instance, parameters: List<Instance>): Instance
}