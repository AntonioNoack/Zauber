package zauber.async

import zauber.IteratorUntilNull
import zauber.Throwable

/**
 * todo use this structure for any method that yields or throws in the compiler,
 *  and somehow use replace try-catch with logic for this
 *  and somehow also await anything that we need to
 * */
abstract class Promise<R, T : Throwable, Y> : IteratorUntilNull<Yieldable<R, T, Y>>() {

    /**
     * whether a return value has been found yet
     * */
    var hasValue: Boolean = false
        private set

    /**
     * set to the return value when it has been found
     * */
    var value: R? = null

    /**
     * wait for the value to be calculated
     * */
    fun await(): R {
        while (true) {
            val response = next()
            when (response) {
                is Returned<R, *, *> -> return response.value
                is Thrown<*, *, *> -> throw response.value
                else -> yield response;
            }
        }
    }

    /**
     * wait for the value to be calculated, but you may handle any exception/yield
     * */
    fun awaitThrown(handler: (T) -> Boolean): R {
        while (true) {
            val response = next()
            if (response is Returned<R, *, *>) {
                return response.value
            } else if (response is Thrown<*, T, *> && handler(response)) {
                // done
            } else {
                if (response is Thrown<*, *, *>) throw response.value
                else yield response.value;
            }
        }
    }

    /**
     * wait for the value to be calculated, but you may handle any exception/yield
     * */
    fun awaitYielded(handler: (Y) -> Boolean): R {
        while (true) {
            val response = next()
            if (response is Returned<R, *, *>) {
                return response.value
            } else if (response is Yielded<*, *, Y> && handler(response.value)) {
                // done
            } else {
                if (response is Thrown<*, *, *>) throw response.value
                else yield response . value;
            }
        }
    }
}