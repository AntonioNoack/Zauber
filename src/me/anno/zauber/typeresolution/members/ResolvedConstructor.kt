package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

class ResolvedConstructor(
    constructor: Constructor,
    context: ResolutionContext, codeScope: Scope,
    matchScore: MatchScore,
) : ResolvedMember<Constructor>(
    constructor, context, codeScope, matchScore,
) {

    override fun getScopeOfResolved(): Scope = resolved.scope

    override fun getTypeFromCall(): Type {
        return resolved.selfTypeI.clazz.typeWithArgs
            .specialize(specialization)
    }

    override fun toString(): String {
        return "ResolvedConstructor(constructor=$resolved, generics=$specialization)"
    }
}