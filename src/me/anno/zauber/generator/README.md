# Generators

My language shall run anywhere, so compiling to different targets is crucial.
They also pose different levels of difficulty:

We
- should start with Java (+union-types, +async?, +new-class-system),
- then JVM Bytecode (+stack-based, +full-resolution),
- then JavaScript/Python (different stdlib),
- then C++ backend (+GC, +lambda-scope-classes?),
- then C (+inheritance, +lambda-scope-classes!!),
- then LLVM (?? completely new),
- then Zig (+built-in allocators) or Rust (+ownership)