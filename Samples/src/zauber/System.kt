package zauber

object System {
    external fun nanoTime(): Long
    external fun currentTimeMillis(): Long
    external fun identityHashCode(obj: Any?): Int
}