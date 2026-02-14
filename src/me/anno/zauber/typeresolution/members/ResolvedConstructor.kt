package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

class ResolvedConstructor(
    ownerTypes: ParameterList, constructor: Constructor,
    context: ResolutionContext, codeScope: Scope,
    matchScore: MatchScore
) : ResolvedMember<Constructor>(ownerTypes, emptyParameterList(), constructor, context, codeScope, matchScore) {

    override fun getScopeOfResolved(): Scope = resolved.scope

    override fun getTypeFromCall(): Type {
        return ClassType(resolved.selfType.clazz, selfTypeParameters)
    }

    override fun toString(): String {
        return "ResolvedConstructor(constructor=$resolved, generics=$selfTypeParameters)"
    }
}