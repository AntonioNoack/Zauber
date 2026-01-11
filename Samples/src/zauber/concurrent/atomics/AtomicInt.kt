package zauber.concurrent.atomics

class AtomicInt(var value: Int) {
    fun incrementAndGet() = ++value
    fun decrementAndGet() = --value
    fun getAndIncrement() = value++
    fun getAndDecrement() = value--
}