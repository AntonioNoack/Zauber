package me.anno.support.python

class EitherOr<A : Any, B : Any> private constructor(val a: A?, val b: B?) {

    constructor(a: A) : this(a, null)
    constructor(b: B, unused: Unit) : this(null, b)

    val isA get() = a != null

    fun <R> map(mapA: (A) -> R, mapB: (B) -> R): R {
        return if (a != null) mapA(a) else mapB(b!!)
    }

}