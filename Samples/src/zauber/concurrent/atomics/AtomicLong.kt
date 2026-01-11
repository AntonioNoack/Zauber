package zauber.concurrent.atomics

class AtomicLong(var value: Long) {
    fun incrementAndGet() = ++value
    fun decrementAndGet() = --value
    fun getAndIncrement() = value++
    fun getAndDecrement() = value--
}