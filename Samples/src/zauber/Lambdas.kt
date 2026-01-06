package zauber

fun interface Function0<R> {
    fun call(): R
}

fun interface Function1<P0, R> {
    fun call(p0: P0): R
}

fun interface Function2<P0, P1, R> {
    fun call(p0: P0, p1: P1): R
}

fun interface Function3<P0, P1, P2, R> {
    fun call(p0: P0, p1: P1, p2: P2): R
}

fun interface Function4<P0, P1, P2, P3, R> {
    fun call(p0: P0, p1: P1, p2: P2, p3: P3): R
}

fun interface Function5<P0, P1, P2, P3, P4, R> {
    fun call(p0: P0, p1: P1, p2: P2, p3: P3, p4: P4): R
}

fun interface Function6<P0, P1, P2, P3, P4, P5, R> {
    fun call(p0: P0, p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): R
}

fun interface Function7<P0, P1, P2, P3, P4, P5, P6, R> {
    fun call(p0: P0, p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): R
}

fun interface Function8<P0, P1, P2, P3, P4, P5, P6, P7, R> {
    fun call(p0: P0, p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7): R
}

inline fun <V, R> V.run(runnable: V.() -> R): R {
    return runnable()
}

inline fun <V, R> V.apply(runnable: V.() -> Unit): V {
    runnable()
    return this
}