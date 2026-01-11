package zauber.math

fun <V : Comparable<V>> min(a: V, b: V): V = if (a < b) a else b
fun <V : Comparable<V>> max(a: V, b: V): V = if (a > b) a else b

fun <V : Comparable<V>> min(a: V, b: V, c: V): V = min(a, min(b, c))
fun <V : Comparable<V>> max(a: V, b: V, c: V): V = max(a, max(b, c))

fun <V : Comparable<V>> min(a: V, b: V, c: V, d: V): V = min(min(a, b), min(c, d))
fun <V : Comparable<V>> max(a: V, b: V, c: V, d: V): V = max(max(a, b), max(c, d))
