package zauber.async

/**
 * things, which can be yielded:
 * this can be used for generators, and for "green threads"
 * */
sealed interface Yielded {

    /**
     * return some value to the callee
     * */
    value class Yield<Y>(val value: Y): Yielded

    /**
     * return some final value to the callee and finish execution
     * */
    value class Returned<R>(val value: R): Yielded

    /**
     * return some critical state to the callee and finish execution
     * */
    value class Thrown<T>(val value: T): Yielded

}