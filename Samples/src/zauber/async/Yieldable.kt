package zauber.async

import zauber.Throwable

/**
 * things, which can be yielded:
 * this can be used for generators, and for "green threads";
 *
 * is either one of these:
 * - Returned
 * - Thrown
 * - Yielded
 * */
sealed interface Yieldable<R, T : Throwable, Y>