package zauber.async

/**
 * return some intermediate state or information to the callee,
 * and maybe later continue execution
 * */
value class Yielded<R, T : Throwable, Y>(
    val yieldedValue: Y,
    val continueRunning: () -> Yielded<R, T, Y>
) : Yieldable<R, T, Y>