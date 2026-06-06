package me.anno.zauber

// todo define memory arenas:
//  - any allocation within .use {} is put into the arena,
//  - .clear() resets it,
//  - if GCed, all instances inside become invalid (danger!)
//  - no GC necessary inside...
//  - empty wrapper in Java, JavaScript, Python and WASM, full in C,C++,LLVM
// todo WASM without GC -> is it better or worse?
class ArenaTests {
}