package me.anno

// todo low-hanging fruits:
//  - allow CLI to execute one script, like in tests
//  - WASM optimizer: we have many repeating, useless structures (set+exclusive get immediately after), ungrouped local-field-IDs, unused local-field-IDs,...
//  - JVM bytecode generator
//  - JVM bytecode loader: create code tokens for debugging
//  - Python generator

/*
* todo yield can be implemented by modifying graph + virtual class, and I think we can do that
*
* todo split value classes into their components (exploded local fields, exploded class properties)
* todo value class methods are two-fold: with reference, with just values
*/

// todo most pressing tasks:
//  1. Make it usable
//    (done) 1. Generate C++ code
//      a) as a first step, everything is a GC/refCount-variable
//      b) later on use proper structs
//      ...
//    2. Simple compiler executable + Project file -> runnable
//      This would finally allow us to create libraries and port to Zauber
//      Fix type inference for underdefined functions and lambdas
//      ...
//    3. We could also just use our runtime + Project file -> (slow, but safe) execution
//  2. Language Features
//    - allow class extension (good for libraries -> just extend data structures)
//    - properly support ref and value types
//    - "in"/"out" variables???
//       -> we could support C#-style "out var resultInMeters: Int", with declaration inside function parameters...
//    - properly implement YIELD
//       -> all method fields + active temporary fields need to become a struct
//       -> or we need to calculate, which to keep at least
//       then we call the sub-class and sub-function...
//      ---------------------
//       ==> we could actually just convert each function with yield into one class plus simplfied functions without yields
//      ---------------------
