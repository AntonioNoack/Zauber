package me.anno.support.jvm

fun main() {
    // todo read a complex class like HashMap,
    //  and decode it fully into simple instructions...
    JVMClassReader.getScope("java/util/ArrayList", null)
        .scope
}