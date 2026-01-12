package zauber.async

/**
 * things, which can be yielded:
 * this can be used for generators, and for "green threads"
 * */
sealed interface Yieldable<R, T : Throwable, Y>