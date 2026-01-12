package zauber.async

/**
 * return some final value to the callee and finish execution
 * */
value class Returned<R, T, Y>(val value: R) : Yieldable<R, T, Y>