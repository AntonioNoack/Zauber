# Generators

My language shall run anywhere, so compiling to different targets is crucial.
They also pose different levels of difficulty:

We
- should start with Java (+union-types, +async?, +new-class-system),
- then JVM Bytecode (+stack-based, +full-resolution),
- then JavaScript/Python (+different stdlib),
- then C++ backend (+GC, +lambda-scope-classes?),
- then C (+inheritance, +lambda-scope-classes!!),
- then LLVM (?? completely new),
- then Zig (+built-in allocators) or Rust (+ownership)

We also need GLSL, but I'm unsure about what to make explicit and implicit:
we cannot start in GLSL, and we may need dynamic allocators,
so we kind of have multiple, different entry points,
and getting to start the program (loading the environment to the GPU) is a challenge, too.
