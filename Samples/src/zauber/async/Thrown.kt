package zauber.async

/**
 * return some critical state to the callee and finish execution
 * */
value class Thrown<R, T : Throwable, Y>(val value: T) : Yieldable<R, T, Y>