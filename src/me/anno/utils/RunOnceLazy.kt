package me.anno.utils

class RunOnceLazy<V>(val generator: () -> V) {

    var hasValue = false
    private var valueI: V? = null

    val value: V
        get() {
            if (!hasValue) {
                hasValue = true
                valueI = generator()
            }
            @Suppress("UNCHECKED_CAST")
            return valueI as V
        }
}