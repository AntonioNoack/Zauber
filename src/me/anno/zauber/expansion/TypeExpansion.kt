package me.anno.zauber.expansion

import me.anno.zauber.types.Scope

// todo somehow expand types... idk, maybe we do this later on...
//  -> no, each method call now can get resolved to more fine-granular candidates
object TypeExpansion {
    fun resolveSpecificCalls(root: Scope) {
        // todo all types are resolved now, so we should create "instantiate" all generic types
        //  to reduce type/traversal complexity (comptime gets "compiled")
    }
}