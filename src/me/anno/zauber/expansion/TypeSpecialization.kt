package me.anno.zauber.expansion

import me.anno.zauber.typeresolution.TypeResolution.forEachScope
import me.anno.zauber.types.Scope

// todo somehow expand types... idk, maybe we do this later on...
//  -> no, each method call now can get resolved to more fine-granular candidates

// todo the goal is to eliminate all generics...
//  but we may need transition instructions...

object TypeSpecialization {
    fun specializeAllGenerics(root: Scope) {
        // todo all types are resolved now, so we should create "instantiate" all generic types
        //  to reduce type/traversal complexity (comptime gets "compiled")
        forEachScope(root, ::specializeGenerics)
    }

    fun specializeGenerics(scope: Scope) {
        for (field in scope.fields) {

        }
        for (method in scope.methods) {

        }
    }
}