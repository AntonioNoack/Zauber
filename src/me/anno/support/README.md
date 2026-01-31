# Support Languages

As long as another programming language uses a similar generics and inheritance system,
we should be able to import library implementations from it, and translate them into Zauber ourselves.

The goal is to use libraries from other languages:
- we get to use other languages
- our environment is suddenly much larger

If we can support Python for example,
we get on equal footing to Nvidia's main libraries.

If we can support Rust, we get all of Bevy.

If we support C++, we might get Boost.

## Planned Language Support
- C
- C++ unless you do crazy things
- C#, because very similar to Java
- Java, because Kotlin compiles to it, and has the same inheritance system, just weak generics,
- Kotlin by default, Zauber is very similar
- Python
- Rust, except for macros maybe

For now, the focus is on making it generally working, not on being perfect.
If you need perfect, use Zauber, or explicit C bindings / external functions.