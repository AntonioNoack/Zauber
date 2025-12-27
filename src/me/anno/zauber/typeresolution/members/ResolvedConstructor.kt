package me.anno.zauber.typeresolution.members

import me.anno.zauber.astbuilder.Constructor
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

class ResolvedConstructor(ownerTypes: List<Type>, constructor: Constructor, context: ResolutionContext) :
    ResolvedCallable<Constructor>(ownerTypes, emptyList(), constructor, context) {

    override fun getTypeFromCall(): Type {
        return ClassType(resolved.selfType.clazz, ownerTypes)
    }

    override fun toString(): String {
        return "ResolvedConstructor(constructor=$resolved, generics=$ownerTypes)"
    }
}