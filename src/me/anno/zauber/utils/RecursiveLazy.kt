package me.anno.zauber.utils

class RecursiveLazy<V>(val generator: () -> V) {

    enum class State {
        UNINITIALIZED,
        GENERATING,
        HAS_VALUE
    }

    var state = State.UNINITIALIZED
    private var valueI: V? = null

    val value: V
        get() = when (state) {
            State.UNINITIALIZED -> {
                state = State.GENERATING
                valueI = generator()
                state = State.HAS_VALUE
                valueI as V
            }
            State.HAS_VALUE -> {
                valueI as V
            }
            State.GENERATING -> throw RecursiveException()
        }

}