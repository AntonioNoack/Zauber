
## Using other languages

Using existing libraries is important, so we'll try to make Zauber compatible with many environments.
Ideally, calling into Python code and back would be easily possible, too 🤔.

### C/C++-Interop

Interacting with languages like C needs definitions.
For the start, there will be external functions:
```kotlin
external fun malloc(size: Long): Pointer<Any?>
```

later, I'd like us to implement a C compiler as part of this compiler, such that we can just "include" .h files,
and use their API directly without manual JNI code.

### JVM Interop

When we compile Zauber to Java or JVM Bytecode, we should be able to easily interop, otherwise,
we probably have to go the C-way.