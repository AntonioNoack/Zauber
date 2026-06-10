# Asynchronous Programming

Java has blocking IO operations, which can occupy whole threads,
and many languages implementing asynchronous flow (unlike Zig) have method-coloring issues: 
methods need to be explicitly marked.

Zauber wants to avoid that, and does that by making async flow implicit.
Any method can `yield` any value, and any parent can catch that.

// todo implement yields returning values

```kotlin
val file = File("MyFile.txt")
file.inputStream().use { input
    val data = input.read() // read() internally can yield a read-request, and continue execution whenver
}
```

Threads can be continued when their data is ready. This is green threads (if implemented properly).

Handlers for yielded values must use the 'async' keyword. Here is some pseudo implementation:
```kotlin
class FileInputStream {
    var buffer: ByteArray
    var position = 0
    var size = 0
    
    fun read(): Int {
        if (position < size) {
            return buffer[position++]
        }
        yield RequestNextBuffer(this)
    }
}

class OSThread {
    val greenThreads = ArrayDeque<() -> Unit>()
    
    fun run() {
        while (true) {
            val last = greenThreads.removeLast()
            val result = async last()
            if (result is Yielded<*>) {
                if (result.yieldedValue is RequestNextBuffer) {
                    // todo ask OS for next buffer, and when available, fill in
                }// else ...
                greenThreads.addFirst(last.continueRunning) // continue later on
            }
        }
        // done
    }
}
```