package zauber.async

import zauber.IteratorUntilNull

/**
 * todo use this structure for any method that yields or throws
 * */
abstract class Promise<R> : IteratorUntilNull<Yielded>() {

    /**
     * whether a return value has been found yet
     * */
    var hasValue: Boolean = false
        private set

    /**
     * set to the return value when it has been found
     * */
    var value: R? = null

}