# Zauber

This is a modern programming language with focus on usability, performance, and memory-safety.

<img src="assets/Logo.png" alt="Mascot for Zauber" style="width:15em"/>

This project is still in its infancy, but I really do want a language without compromises and
constantly thinking about switching from Kotlin to Zig or Rust.

## Learn Zauber

I've also written a small blog/tutorial in this project to get you started:
[Learn Zauber today](Learn/01_Introduction.md).

The compiler is not working yet, so...

## Motivation

My main motivation is
- a) [CodeGeneration] being able to generate IO code automatically (making reflections compile time, and therefore cheaper)
- b) [GC Overhead] not constantly having to think about native-types-as-generics/short-lived-objects-Overhead and GC-lag
- c) [GPU Debugging] being able to run and debug GLSL code on the CPU (by compiling Zauber to both LOL)
- d) [Native Performance], [JNI Overhead], [Vectorization] not being constantly reminded that my code could run twice as fast
- e) [Libraries] lots of libraries are written in C/C++, but finding/creating bindings is always a pain

Side motivation:
- Getting independent of Intellij Idea (see https://youtrack.jetbrains.com/issue/IDEA-68854, hot topic for 14 years, and still unsolved),
- Developing our own language tooling without needing the Intellij SDK

## Goals:
- Kotlin-style
- Native C/C++ performance
- Rust-style enums (e.g. for errors) AKA union types → these are just sealed classes, aren't they?
- Rust-style macros (or at least their power)
- Struct types aka not storing the object header where not necessary
- Easy struct of arrays
- Optionally strong generics AKA making ArrayList<Float> contain a FloatArray instead of an Array<Float>
- GLSL cross compilation: debug statements on GPU & running the same algorithms on CPU
- Compile-Time execution using an interpreter
- Variable-Index-Based Virtual Machine code (could be converted to stack-based VM code for JVM)
- Fixed Point arithmetic without OOP/GC overhead
- Stack-located variables
- Comptime execution / code generation
- Python-like Multi-assignment-style to swap fields: (a,b) = (b,a)

I'd like to compile Rem's Engine (200k LOC + stdlib) to C++ in less than 30 seconds.
Ideally much faster.

## Milestones:

(can be checked off when my game engine and this compiler can be processed)

- [x] Tokenizing Kotlin
- [x] Parsing Kotlin to an AST
- [ ] Type Resolution
- [ ] Basic Code Generation
- [ ] Basic GC for JVM-style objects (default for Kotlin compatibility)
- [ ] Different Allocators
- [ ] ...
- [ ] Compile and run the compiler using Zauber
- [ ] Compile and run Rem's Engine using Zauber
- [ ] Compile to a native language (or LLVM IR)
- [ ] Compile to WASM (for running in the browser & safe containers)

## Type Definitions

I want to be able to declare rich types like TypeScript,
and not to pay the runtime overhead, e.g.

```kotlin
value class Euro(val value: Int) {
// plus, times, ...
}

typealias FloatLike = Half|Float|Double
typealias SeriComp = Serializable&Comparable<*>
typealias NotFloat = Number&!FloatLike
```

Static analysis can also be really helpful and powerful,
so it would be nice to have comptime-restricted types, e.g.

```kotlin
val x: Int[in 0 until 100] = 45
val somePrime: Int[isPrime(it)] = 17
val cheapEnum: String["a","b","c"] = "a"
```

## Allocation Styles
(not yet implemented)

```kotlin
data class Vec2i(val x: Int, val y: Int)
value var x = Vec2i(0,0) // will be stored on the stack
val y = Vec2i(0,0) // will be ref-counted or GCed
value val array = Array(10) { Vec2i(it,it*it) } // all entries will share one GC/type-overhead; constant size -> could be stored on the stack
val floatArray = arrayOf(0f, 1f, 2f) // 4 bytes per entry, because type can be shared, and floats is marked as a value class
```

floatArrayOf() etc will become obsolete and will be marked as deprecated, because arrayOf() has the same meaning.

### Reflection

I'd like the reflection API to be completely comptime like in Zig,
so if you don't use it, you don't need the (space)overhead.

### Progress Estimation

Total Progress: 1.2 %

```yaml
- Kotlin-Style:
  - Tokenizer: 90% of 1%
  - AST: 80% of 3%
  - Typealias: 50% of 0.2%
  - Type-Resolution: 30% of 4%
  - Baking(comptime) Generics: 0% of 2%
  - Dependency-Optimization: 0% of 4%
- Rust-style Macros: 0% of 3%
- Compile to C/C++: 1% of 3%
- Choose Allocator for Instantiation: 0% of 3%
- Arena Allocator: 0% of 2%
- Store struct members on stack: 0% of 3%
- Automatic Struct Of Arrays: 0% of 2%
- Compile to JVM: 0% of 3%
- Compile to WASM: 0% of 3%
- Debug-Compile to x86 directly: 0% of 4%
- Hot-reloading functions: 0% of 5%
- JVM bindings for FileIO, OpenGL/GLFW, println, ...: 0% of 3%
- WASM bindings for async IO, WebGL, println, ...: 0% of 3%
- C++ bindings for FileIO, OpenGL, native libraries, ...: 0% of 3%
- Automatically colored asynchronous Methods: 0% of 3%
- Fixed-Point numbers: 0% of 3%
- Lambdas: 20% of 3%
- Garbage Collector: 0% of 2%
- Multithreading and Parallel GC: 0% of 5%
- Completely Immutable Objects: 0% of 3%
- CompileTime Interpreter: 0% of 5%
- VisualStudioCode Extension for syntax checking: 0% of 3%
- VisualStudioCode Extension for semantic checking: 0% of 5%
- Intellij Idea Extension for syntax checking: 0% of 3%
- Intellij Idea Extension for semantic checking: 0% of 5%
- Custom Code Editor (Necromicon?, Grimoire): 0% of 5%
...
# yes, these probably don't add up to 100%
```

## Naming

Programming languages are magic to even most developers, and I like the German words "Zauber" and "Zauberei".
Compilers are/were pretty magic to me before I got well into this project too, although I had some [JVM2WASM](https://github.com/AntonioNoack/jvm2wasm) experience.

I'm also thinking about renaming it to "fux" (German & English mixed) or "fox", but idk...
