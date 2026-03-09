package me.anno.zauber2

// todo new flow:
//  - make states & parsing lazy, specialization already kind of is
//  - only specialize native and union super-types, and comptime parameters
//  1. index class structure, fields and methods
//  2. resolve type and field names in their scopes
//  3. specialize/resolve code -> create dumb classes and (helper)functions
//  4. produce runnable code

// todo produce runnable code step-by-step to avoid complexity?

// todo from the get-go we only need top-level fields/functions/classes
//  anything beyond that can be lazy (we want compilation to be quick & lazy)

// todo when specializing, compute comptime values
// todo introduce comptime numbers, which are fully accurate

// todo make creating and managing modules easy, so we can easily create truly modular modules,
//  and e.g. allow declaring external types: their implementation will come later, but we assume some properties

fun main() {
    // todo test Zauber2


}