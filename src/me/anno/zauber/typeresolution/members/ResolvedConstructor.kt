package me.anno.zauber.typeresolution.members

import me.anno.zauber.astbuilder.Constructor
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

class ResolvedConstructor(ownerTypes: ParameterList, constructor: Constructor, context: ResolutionContext) :
    ResolvedCallable<Constructor>(ownerTypes, emptyParameterList(), constructor, context) {

    override fun getTypeFromCall(): Type {
        return ClassType(resolved.selfType.clazz, ownerTypes)
    }

    override fun toString(): String {
        return "ResolvedConstructor(constructor=$resolved, generics=$ownerTypes)"
    }
}