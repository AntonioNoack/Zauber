package zauber.async

import zauber.Boolean
import zauber.Iterator
import zauber.Throwable

/**
 * todo use this structure for any method that yields or throws in the compiler,
 *  and somehow use replace try-catch with logic for this
 *  and somehow also await anything that we need to
 * */
class Promise<R, T : Throwable, Y>(
    var currentState: Yieldable<R, T, Y>
) : Iterator<Yieldable<R, T, Y>> {

    /**
     * whether a return value has been found yet
     * */
    val hasValue: Boolean
        get() = currentState is Returned<R, *, *>

    /**
     * set to the return value when it has been found
     * */
    val value: R
        get() = (currentState as Returned<R, *, *>).value

    /**
     * set to the return value when it has been found
     * */
    val valueOrNull: R
        get() = if (hasValue) value else null

    /**
     * wait for the value to be calculated
     * */
    fun await(): R {
        while (true) {
            when (val response = next()) {
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
                else yield (response as Yielded<*,*,Y>).value;
            }
        }
    }

    /**
     * wait for the value to be calculated, but you may handle any exception/yield
     * */
    fun awaitYielded(handler: (Y) -> Boolean): R {
        while (true) {
            val response = next()
            if (hasValue) {
                return value
            } else if (response is Yielded<*, *, Y> && handler(response.yieldedValue)) {
                // done
            } else {
                if (response is Thrown<*, *, *>) throw response.value
                else yield (response as Yielded<*,*,Y>).value;
            }
        }
    }

    override fun hasNext(): Boolean {
        return currentState is Yielded<R, T, Y>
    }

    override fun next(): Yieldable<R, T, Y> {
        currentState = (currentState as Yielded<R, T, Y>).continueRunning()
        return currentState
    }
}